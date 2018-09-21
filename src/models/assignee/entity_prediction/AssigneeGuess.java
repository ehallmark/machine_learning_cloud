package models.assignee.entity_prediction;

import graphical_modeling.util.Pair;
import lombok.Getter;
import org.nd4j.linalg.primitives.AtomicDouble;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class AssigneeGuess {

    private static Pair<String, Double> bestGuessAssignee(String[] inventors, LocalDate filingDate, Map<String, List<AssigneeAndDate>> assigneeAndDateMap) {
        Map<String, Map<String,AtomicDouble>> inventorScoreMaps = new HashMap<>();
        for(String inventor : inventors) {
            Map<String,AtomicDouble> scoreMap = new HashMap<>();
            inventorScoreMaps.put(inventor, scoreMap);
            List<AssigneeAndDate> assigneeAndDates = assigneeAndDateMap.get(inventor);
            if(assigneeAndDates != null) {
                for(AssigneeAndDate assigneeAndDate : assigneeAndDates) {
                    scoreMap.putIfAbsent(assigneeAndDate.getAssignee(), new AtomicDouble(0));
                    double dateDiff = Math.abs((double) filingDate.getYear() + ((double) filingDate.getMonthValue() - 1.0) / 12.0 - ((double) assigneeAndDate.getDate().getYear() + ((double) assigneeAndDate.getDate().getMonthValue() - 1.0) / 12.0));
                    if (dateDiff < 1.0) {
                        scoreMap.get(assigneeAndDate.getAssignee()).getAndAdd(1.0 / (1.0 + dateDiff));
                    }
                }
            }
        }

        final double totalScore = inventorScoreMaps.values().stream().flatMap(scoreMap->scoreMap.values().stream())
                .mapToDouble(d->d.get()).sum();

        Map.Entry<String,Double> bestEntry = inventorScoreMaps.values().stream().flatMap(map->map.entrySet().stream()).collect(Collectors.groupingBy(e->e.getKey(), Collectors.summingDouble(e->e.getValue().get())))
        .entrySet().stream().max(Comparator.comparingDouble(e->e.getValue())).orElse(null);

        if(bestEntry!=null) {
            final double score = bestEntry.getValue() / totalScore;
            return new Pair<>(bestEntry.getKey(), score);
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Map<String, List<AssigneeAndDate>> assigneeAndDateMap = new HashMap<>();

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select assignee,inventor,date from assignees_inventors");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();
        long count = 0L;
        while(rs.next()) {
            String assignee = rs.getString(1);
            String inventor = rs.getString(2);
            LocalDate date = rs.getDate(3).toLocalDate();
            AssigneeAndDate assigneeAndDate = new AssigneeAndDate(assignee, date);
            assigneeAndDateMap.putIfAbsent(inventor, new ArrayList<>());
            assigneeAndDateMap.get(inventor).add(assigneeAndDate);
            if(count%10000==9999) {
                System.out.println("Loaded "+count);
            }
            count ++;
        }
        rs.close();
        ps.close();


        ps = conn.prepareStatement("select publication_number_full, filing_date, inventor_harmonized, assignee_harmonized from patents_global where inventor_harmonized is not null and array_length(inventor_harmonized, 1) > 0 and filing_date is not null");
        ps.setFetchSize(10);
        rs = ps.executeQuery();
        count = 0L;
        while(rs.next()) {
            String publicationNumberFull = rs.getString(1);
            LocalDate date = rs.getDate(2).toLocalDate();
            String[] inventors = (String[]) rs.getArray(3).getArray();
            Pair<String, Double> bestGuessAssignee = bestGuessAssignee(inventors, date, assigneeAndDateMap);
            if(bestGuessAssignee!=null) {
                System.out.println(publicationNumberFull+": Found "+bestGuessAssignee._1+" with score: "+bestGuessAssignee._2);
                try {
                    String[] assignees = (String[]) rs.getArray(4).getArray();
                    if(assignees!=null) {
                        System.out.println("Actual assignees: "+String.join("; ", assignees));
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            if(count%10000==9999) {
                System.out.println("Predicted "+count);
            }
            count ++;
        }

        rs.close();
        ps.close();

    }
}


class AssigneeAndDate {
    @Getter
    private String assignee;
    @Getter
    private LocalDate date;
    public AssigneeAndDate(String assignee, LocalDate date) {
        this.assignee=assignee;
        this.date=date;
    }
}
