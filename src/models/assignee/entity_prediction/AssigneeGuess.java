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
import java.util.function.Function;
import java.util.stream.Collectors;

public class AssigneeGuess {

    static Pair<String, Double> bestGuessAssignee(String[] inventors, LocalDate filingDate, Map<String, Map<LocalDate, String>> assigneeAndDateMap, final double maxDateDiff, final double minScore, final double minTotalScore, final Function<Double, Double> dateDiffFunc) {
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
                        scoreMap.get(assignee).getAndAdd(dateDiffFunc.apply(dateDiff));
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
        boolean predict = true;

        Connection conn = Database.getConn();
        CSVReader reader = new CSVReader(new BufferedReader(new FileReader(new File("assignees_inventors.csv"))));
        PreparedStatement ps;
        ResultSet rs;
        long count = 0L;
        Iterator<String[]> iterator = reader.iterator();
        iterator.next(); // skip first line
        while(iterator.hasNext()) {
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
        PreparedStatement insertStatement = conn.prepareStatement("insert into assignee_guesses (publication_number_full, assignee, score) values (?, ?, ?) on conflict (publication_number_full) do update set (assignee,score)=(excluded.assignee,excluded.score)");
        count = 0L;
        Map<String, Object[]> data = new HashMap<>();
        while(rs.next() && (predict || count < 5000000)) {
            String publicationNumberFull = rs.getString(1);
            LocalDate date = rs.getDate(2).toLocalDate();
            String[] inventors = (String[]) rs.getArray(3).getArray();
            if(predict) {
                Pair<String, Double> bestGuessAssignee = AssigneeGuess.bestGuessAssignee(inventors, date, assigneeAndDateMap, 0.2375, 0.01323, 0.06689, AssigneeSolution.dateDiffFunctions.get(1));
                if(bestGuessAssignee!=null) {
                    ps.setString(1, publicationNumberFull);
                    ps.setString(2, bestGuessAssignee._1);
                    ps.setDouble(3, bestGuessAssignee._2);
                    ps.executeUpdate();
                }
            } else {
                try {
                    if (rs.getArray(4) != null) {
                        String[] assignees = (String[]) rs.getArray(4).getArray();
                        if (assignees != null && assignees.length > 0) {
                            data.put(publicationNumberFull, new Object[]{date, inventors, assignees});
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if(count%10000==9999) {
                conn.commit();
                System.out.println("Seen "+count);
            }
            count ++;
        }

        rs.close();
        ps.close();

        if(predict) {
            insertStatement.close();
            conn.commit();
        } else {
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

            GeneticAlgorithm<Solution> algorithm = new GeneticAlgorithm<>(solutionCreator, 16, listener);
            algorithm.simulate(Long.MAX_VALUE, 0.5, 0.5);
        }

        conn.close();

    }
}

class AssigneeSolution implements Solution {
    static final List<Function<Double,Double>> dateDiffFunctions = Arrays.asList(
           // d -> (1.0 / (1.0 + d)),
           // d -> (1.0 / Math.exp(d)),
            d -> (1.0 / (1.0 + Math.sqrt(d))),
            d -> (1.0 / (1.0 + Math.exp(d)))
           // d -> (1.0 / (Math.log(Math.E + d)))
    );
    private static final double TOTAL_SCORE_RANGE = 1.0;
    private static final double DATE_DIFF_RANGE = 2.0;
    private static final double MIN_SCORE_RANGE = 0.1;
    private static final Random random = new Random(235928);
    private double score;
    private final double maxDateDiff;
    private final double minScore;
    private final double minTotalScore;
    private final Function<Double,Double> dateDiffFunc;
    private Map<String,Object[]> data;
    private Map<String, Map<LocalDate, String>> assigneeAndDateMap;
    public AssigneeSolution(final double maxDateDiff, final double minScore, final double minTotalScore, Function<Double,Double> dateDiffFunc, Map<String,Object[]> data, Map<String, Map<LocalDate, String>> assigneeAndDateMap) {
        this.maxDateDiff=maxDateDiff;
        this.dateDiffFunc=dateDiffFunc;
        this.assigneeAndDateMap = assigneeAndDateMap;
        this.data=data;
        this.minScore=minScore;
        this.minTotalScore=minTotalScore;
    }

    public AssigneeSolution(Map<String,Object[]> data, Map<String, Map<LocalDate, String>> assigneeAndDateMap) {
        this(0.87515933 + (random.nextDouble()*DATE_DIFF_RANGE-DATE_DIFF_RANGE/2),
                0.035618 + (random.nextDouble()*MIN_SCORE_RANGE-MIN_SCORE_RANGE/2),
                0.4942404 + (random.nextDouble()*TOTAL_SCORE_RANGE-TOTAL_SCORE_RANGE/2),
                dateDiffFunctions.get(random.nextInt(dateDiffFunctions.size())), data, assigneeAndDateMap);
    }

    @Override
    public double fitness() {
        return score;
    }

    @Override
    public void calculateFitness() {
        long t0 = System.currentTimeMillis();
        score = data.entrySet().stream().mapToDouble(e->{
            LocalDate date = (LocalDate) e.getValue()[0];
            String[] inventors = (String[])e.getValue()[1];
            String[] assignees = (String[])e.getValue()[2];
            Pair<String, Double> bestGuessAssignee = AssigneeGuess.bestGuessAssignee(inventors, date, assigneeAndDateMap, maxDateDiff, minScore, minTotalScore, dateDiffFunc);
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
        long t1 = System.currentTimeMillis();
        System.out.println("Time to calculate fitness: "+ (((double)t1-t0)/1000) + " seconds");
        System.out.println("\tParams: "+toString());
    }

    @Override
    public Solution mutate() {
        return new AssigneeSolution(
                (maxDateDiff + (random.nextDouble() * DATE_DIFF_RANGE))/2.0,
                (minScore + (random.nextDouble() * MIN_SCORE_RANGE))/2.0,
                (minTotalScore + (random.nextDouble() * TOTAL_SCORE_RANGE))/2,
                random.nextBoolean() ? dateDiffFunc : (dateDiffFunctions.get(random.nextInt(dateDiffFunctions.size()))),
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
                random.nextBoolean() ? dateDiffFunc : other.dateDiffFunc,
                data,
                assigneeAndDateMap
        );
    }

    @Override
    public int compareTo(@NotNull Solution o) {
        return Double.compare(o.fitness(), fitness());
    }

    @Override
    public String toString() {
        return "Score: "+fitness()+"; Params: maxDateDiff: "+maxDateDiff+", minScore: "+minScore+", minTotalScore: "+minTotalScore+", Function idx: "+dateDiffFunctions.indexOf(dateDiffFunc);
    }
}