package models.assignee.entity_prediction;

import com.opencsv.CSVReader;
import graphical_modeling.util.Pair;
import org.nd4j.linalg.primitives.AtomicDouble;
import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class AssigneeGuess {

    private static Pair<String, Double> bestGuessAssignee(String[] inventors, LocalDate filingDate, Map<String, Map<LocalDate, String>> assigneeAndDateMap) {
        Map<String, Map<String,AtomicDouble>> inventorScoreMaps = new HashMap<>();
        for(String inventor : inventors) {
            Map<String,AtomicDouble> scoreMap = new HashMap<>();
            inventorScoreMaps.put(inventor, scoreMap);
            Map<LocalDate, String> assigneeAndDates = assigneeAndDateMap.get(inventor);
            if(assigneeAndDates != null) {
                assigneeAndDates.forEach((date, assignee)->{
                    scoreMap.putIfAbsent(assignee, new AtomicDouble(0));
                    double dateDiff = Math.abs((double)filingDate.getYear() + ((double)filingDate.getMonthValue()-1.0)/12.0 - ((double)date.getYear() + ((double)date.getMonthValue()-1.0)/12.0));
                    if(dateDiff < 1.0) {
                        scoreMap.get(assignee).getAndAdd(1.0 / (1.0 + dateDiff));
                    }
                });
            }
        }

        final double totalScore = inventorScoreMaps.values().stream().flatMap(scoreMap->scoreMap.values().stream())
                .mapToDouble(d->d.get()).sum();

        Map.Entry<String,Double> bestEntry = inventorScoreMaps.values().stream().flatMap(map->map.entrySet().stream()).collect(Collectors.groupingBy(e->e.getKey(), Collectors.summingDouble(e->e.getValue().get())))
        .entrySet().stream().max(Comparator.comparingDouble(e->e.getValue())).orElse(null);

        if(totalScore > 0 && bestEntry!=null) {
            final double score = bestEntry.getValue() / totalScore;
            if (score > 0.5) {
                return new Pair<>(bestEntry.getKey(), score);
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Map<String, Map<LocalDate, String>> assigneeAndDateMap = new HashMap<>(50000);

        Connection conn = Database.getConn();
        CSVReader reader = new CSVReader(new BufferedReader(new FileReader(new File("assignees_inventors.csv"))));
        PreparedStatement ps;
        ResultSet rs;
        long count = 0L;
        Iterator<String[]> iterator = reader.iterator();
        iterator.next(); // skip first line
        while(iterator.hasNext() && count < 5000000) {
            String[] lines = iterator.next();
            String assignee = lines[0].toUpperCase();
            String inventor = lines[1].toUpperCase();
            LocalDate date = LocalDate.parse(lines[2], DateTimeFormatter.ISO_DATE);
            assigneeAndDateMap.putIfAbsent(inventor, new HashMap<>());
            assigneeAndDateMap.get(inventor).put(date, assignee);
            if(count%10000==9999) {
                System.out.println("Loaded "+count);
            }
            count ++;
        }
        reader.close();


        ps = Database.newSeedConn().prepareStatement("select publication_number_full, filing_date, inventor_harmonized, assignee_harmonized from patents_global where inventor_harmonized is not null and array_length(inventor_harmonized, 1) > 0 and filing_date is not null");
        ps.setFetchSize(10);
        rs = ps.executeQuery();
        PreparedStatement insertStatement = conn.prepareStatement("insert into assignee_guesses (publication_number_full, assignee_guess) values (?, ?) on conflict (publication_number_full) do update set assignee_guess=excluded.assignee_guess");
        count = 0L;
        long correctCount = 0L;
        long wrongCount = 0L;
        long available = 0L;
        while(rs.next()) {
            String publicationNumberFull = rs.getString(1);
            LocalDate date = rs.getDate(2).toLocalDate();
            String[] inventors = (String[]) rs.getArray(3).getArray();
            Pair<String, Double> bestGuessAssignee = bestGuessAssignee(inventors, date, assigneeAndDateMap);
            if(bestGuessAssignee!=null) {
                //System.out.println(publicationNumberFull+": Found "+bestGuessAssignee._1+" with score: "+bestGuessAssignee._2);
                insertStatement.setString(1, publicationNumberFull);
                insertStatement.setString(2, bestGuessAssignee._1);
                insertStatement.executeUpdate();
                try {
                    if(rs.getArray(4)!=null) {
                        String[] assignees = (String[]) rs.getArray(4).getArray();
                        if (assignees != null && assignees.length > 0) {
                            // System.out.println("Actual assignees: "+String.join("; ", assignees));
                            boolean found = false;
                            for (String assignee : assignees) {
                                if (bestGuessAssignee._1.equals(assignee)) {
                                    found = true;
                                }
                            }
                            if (found) {
                                correctCount++;
                            } else {
                                wrongCount++;
                            }
                        }
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
                available++;
            }
            if(count%10000==9999) {
                long missing = count - available;
                conn.commit();
                System.out.println("Seen "+count+", Predicted: "+available+", Correct: "+correctCount+", Wrong: "+wrongCount + ", Missing: "+missing+", Accuracy: "+((double)correctCount)/(wrongCount+correctCount));
            }
            count ++;
        }

        rs.close();
        ps.close();
        conn.commit();
        conn.close();

    }
}