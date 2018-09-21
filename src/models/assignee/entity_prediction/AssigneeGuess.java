package models.assignee.entity_prediction;

import com.opencsv.CSVReader;
import graphical_modeling.util.Pair;
import models.genetics.GeneticAlgorithm;
import models.genetics.Listener;
import models.genetics.Solution;
import models.genetics.SolutionCreator;
import org.jetbrains.annotations.NotNull;
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

    static Pair<String, Double> bestGuessAssignee(String[] inventors, LocalDate filingDate, Map<String, Map<LocalDate, String>> assigneeAndDateMap, final double maxDateDiff, final double minScore, final double minTotalScore) {
        Map<String, Map<String,AtomicDouble>> inventorScoreMaps = new HashMap<>();
        for(String inventor : inventors) {
            Map<String,AtomicDouble> scoreMap = new HashMap<>();
            inventorScoreMaps.put(inventor, scoreMap);
            Map<LocalDate, String> assigneeAndDates = assigneeAndDateMap.get(inventor);
            if(assigneeAndDates != null) {
                assigneeAndDates.forEach((date, assignee)->{
                    scoreMap.putIfAbsent(assignee, new AtomicDouble(0));
                    double dateDiff = Math.abs((double)filingDate.getYear() + ((double)filingDate.getMonthValue()-1.0)/12.0 - ((double)date.getYear() + ((double)date.getMonthValue()-1.0)/12.0));
                    if(dateDiff < maxDateDiff) {
                        scoreMap.get(assignee).getAndAdd(1.0 / (1.0 + dateDiff));
                    }
                });
            }
        }

        final double totalScore = inventorScoreMaps.values().stream().flatMap(scoreMap->scoreMap.values().stream())
                .mapToDouble(d->d.get()).sum();

        Map.Entry<String,Double> bestEntry = inventorScoreMaps.values().stream().flatMap(map->map.entrySet().stream()).collect(Collectors.groupingBy(e->e.getKey(), Collectors.summingDouble(e->e.getValue().get())))
        .entrySet().stream().max(Comparator.comparingDouble(e->e.getValue())).orElse(null);

        if(totalScore > minTotalScore && bestEntry!=null) {
            final double score = bestEntry.getValue() / totalScore;
            if (score > minScore) {
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

        Map<String, Object[]> data = new HashMap<>();
        while(rs.next() && count < 100000) {
            String publicationNumberFull = rs.getString(1);
            LocalDate date = rs.getDate(2).toLocalDate();
            String[] inventors = (String[]) rs.getArray(3).getArray();
            try {
                if(rs.getArray(4)!=null) {
                    String[] assignees = (String[]) rs.getArray(4).getArray();
                    if (assignees != null && assignees.length > 0) {
                        data.put(publicationNumberFull, new Object[]{date, inventors, assignees});
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }

            if(count%10000==9999) {
                long missing = count - available;
                conn.commit();
                final double accuracy = ((double)correctCount)/(wrongCount+correctCount);
                final double predictPercent = ((double)available) / count;
                final double score = accuracy * predictPercent;
                System.out.println("Score: "+score+", Seen "+count+", Predicted: "+available+", Prediction %: "+predictPercent+", Correct: "+correctCount+", Wrong: "+wrongCount + ", Missing: "+missing+", Accuracy: "+accuracy);
            }
            count ++;
        }

        rs.close();
        ps.close();

        SolutionCreator solutionCreator = new SolutionCreator() {
            @Override
            public Collection<Solution> nextRandomSolutions(int n) {
                List<Solution> solutions = new ArrayList<>();
                for(int i = 0; i < n; i++) {
                    solutions.add(new AssigneeSolution(data, assigneeAndDateMap));
                }
                return solutions;
            }
        };

        Listener listener = new Listener() {
            @Override
            public void print(Solution solution) {
                System.out.println("Solution: "+solution.toString());
            }
        };
        GeneticAlgorithm<Solution> algorithm = new GeneticAlgorithm<>(solutionCreator, 5, listener, 3);
        algorithm.simulate(Long.MAX_VALUE, 0.5, 0.5);


        insertStatement.close();
        conn.commit();
        conn.close();

    }
}

class AssigneeSolution implements Solution {
    private static final Random random = new Random(235928);
    private double score;
    private final double maxDateDiff;
    private final double minScore;
    private final double minTotalScore;
    private Map<String,Object[]> data;
    private Map<String, Map<LocalDate, String>> assigneeAndDateMap;
    public AssigneeSolution(final double maxDateDiff, final double minScore, final double minTotalScore, Map<String,Object[]> data, Map<String, Map<LocalDate, String>> assigneeAndDateMap) {
        this.maxDateDiff=maxDateDiff;
        this.assigneeAndDateMap = assigneeAndDateMap;
        this.data=data;
        this.minScore=minScore;
        this.minTotalScore=minTotalScore;
    }

    public AssigneeSolution(Map<String,Object[]> data, Map<String, Map<LocalDate, String>> assigneeAndDateMap) {
        this(random.nextDouble()*4, random.nextDouble(), random.nextDouble()*10.0, data, assigneeAndDateMap);
    }

    @Override
    public double fitness() {
        return score;
    }

    @Override
    public void calculateFitness() {
        score = data.entrySet().stream().mapToDouble(e->{
            String publicationNumberFull = e.getKey();
            LocalDate date = (LocalDate) e.getValue()[0];
            String[] inventors = (String[])e.getValue()[1];
            String[] assignees = (String[])e.getValue()[2];
            Pair<String, Double> bestGuessAssignee = AssigneeGuess.bestGuessAssignee(inventors, date, assigneeAndDateMap, maxDateDiff, minScore, minTotalScore);
            if(bestGuessAssignee!=null) {
                //System.out.println(publicationNumberFull+": Found "+bestGuessAssignee._1+" with score: "+bestGuessAssignee._2);

                // System.out.println("Actual assignees: "+String.join("; ", assignees));
                boolean found = false;
                for (String assignee : assignees) {
                    if (bestGuessAssignee._1.equals(assignee)) {
                        found = true;
                    }
                }
                if (found) {
                    return 1.0;
                }
            }
            return 0.0;
        }).sum()/data.size();
    }

    @Override
    public Solution mutate() {
        return new AssigneeSolution(
                random.nextBoolean() ? maxDateDiff : random.nextDouble() * 4,
                random.nextBoolean() ? minScore : random.nextDouble(),
                random.nextBoolean() ? minTotalScore : random.nextDouble() * 10.0,
                data,
                assigneeAndDateMap
        );
    }

    @Override
    public Solution crossover(Solution _other) {
        AssigneeSolution other = (AssigneeSolution) _other;
        return new AssigneeSolution(
                random.nextBoolean() ? maxDateDiff : other.maxDateDiff,
                random.nextBoolean() ? minScore : other.minScore,
                random.nextBoolean() ? minTotalScore : other.minTotalScore,
                data,
                assigneeAndDateMap
        );
    }

    @Override
    public int compareTo(@NotNull Solution o) {
        return Double.compare(fitness(), o.fitness());
    }

    @Override
    public String toString() {
        return "Score: "+fitness()+"; Params: maxDateDiff: "+maxDateDiff+", minScore: "+minScore+", minTotalScore: "+minTotalScore;
    }
}