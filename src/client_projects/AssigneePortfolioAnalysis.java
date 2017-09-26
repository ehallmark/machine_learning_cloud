package client_projects;

import models.keyphrase_prediction.scorers.TermhoodScorer;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import tools.OpenMapBigRealMatrix;
import user_interface.ui_models.attributes.computable_attributes.ComputableAssigneeAttribute;
import user_interface.ui_models.attributes.computable_attributes.PortfolioSizeAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/26/17.
 */
public class AssigneePortfolioAnalysis {
    private static final int minPortfolioSize = 500;
    private static final int maxCpcLength = 10;
    private static final int N1 = 1000;
    private static final int N2 = 50;
    public static void main(String[] args) {
        List<String> allCpcCodes = Database.getClassCodes().parallelStream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct().collect(Collectors.toList());
        List<String> allAssignees = new ArrayList<>(Database.getAssignees());
        Map<String,Set<String>> patentToCpcCodeMap = new AssetToCPCMap().getPatentDataMap();
        Map<String,Collection<String>> finalAssigneeToPatentMap;
        Map<String,Integer> allCpcCodeToIndexMap = buildIdxMap(allCpcCodes);
        Map<String,Integer> allAssigneeToIndexMap = buildIdxMap(allAssignees);
        List<String> allAssignees1;
        {
            // step 1
            //  get all assignees with portfolio size > 1000
            Map<String,Collection<String>> assigneeToPatentMap = new AssigneeToAssetsMap().getPatentDataMap();
            allAssignees1 = allAssignees.parallelStream().filter(assignee->{
                return Database.possibleNamesForAssignee(assignee).stream().mapToInt(possibleName->{
                    return assigneeToPatentMap.getOrDefault(possibleName, Collections.emptyList()).size();
                }).sum() >= minPortfolioSize;
            }).collect(Collectors.toList());
            System.out.println("Found assignees after stage 1: "+allAssignees1.size());

            finalAssigneeToPatentMap = allAssignees.parallelStream().map(assignee->{
                Collection<String> allAssets = Database.possibleNamesForAssignee(assignee).stream().flatMap(name->assigneeToPatentMap.getOrDefault(name,Collections.emptyList()).stream())
                        .collect(Collectors.toSet());
                return new Pair<>(assignee,allAssets);
            }).collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));
        }

        List<String> allAssignees2;
        {
            // step 2
            //  calculate tech density per assignee
            Map<String,Integer> assignee1ToIndexMap = buildIdxMap(allAssignees1);
            RealMatrix matrix = buildMatrix(allAssignees1,allCpcCodes,patentToCpcCodeMap,finalAssigneeToPatentMap, assignee1ToIndexMap, allCpcCodeToIndexMap);
            System.out.println("Computing density per assignee");
            Map<String,Double> densityPerAssignee = computeRowWiseDensity(matrix, allAssignees1, assignee1ToIndexMap);

            allAssignees2 = densityPerAssignee.entrySet().parallelStream().sorted((e1,e2)->{
                return e2.getValue().compareTo(e1.getValue());
            }).limit(N1).map(e->e.getKey()).collect(Collectors.toList());
            System.out.println("Num assignees found after stage 2: "+allAssignees2.size());
        }

        List<Pair<String,Double>> allAssignees3WithScores;
        {
            // step 3
            //  calculate assignee density per cpc
            AtomicInteger cnt = new AtomicInteger(0);
            allAssignees3WithScores = allAssignees2.parallelStream().map(assignee->{
                System.out.println("Computing density per assignee");
                Collection<String> assigneePatents = finalAssigneeToPatentMap.get(assignee);
                List<String> assigneeCpcCodesWithDups = assigneePatents.parallelStream().flatMap(p->patentToCpcCodeMap.getOrDefault(p,Collections.emptySet()).stream())
                        .map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc)
                        .collect(Collectors.toList());
                long numPac = assigneeCpcCodesWithDups.size();
                Map<String,Long> Pac = assigneeCpcCodesWithDups.parallelStream().collect(Collectors.groupingBy(e->e,Collectors.counting()));
                List<String> assigneeCpcCodes = assigneeCpcCodesWithDups.parallelStream().distinct().collect(Collectors.toList());
                if(Pac.size()!=assigneeCpcCodes.size()) throw new RuntimeException("Error in assignee cpc codes size");

                double score;
                if(assigneeCpcCodes.size()>0) {
                    Map<String,Integer> cpcIdxMap = buildIdxMap(assigneeCpcCodes);
                    RealMatrix matrix = buildMatrix(allAssignees, assigneeCpcCodes, patentToCpcCodeMap, finalAssigneeToPatentMap, allAssigneeToIndexMap, cpcIdxMap);
                    score = assigneeCpcCodes.isEmpty() ? Double.MAX_VALUE : computeColumnWiseDensity(matrix, assigneeCpcCodes, cpcIdxMap).entrySet().parallelStream()
                            .mapToDouble(e -> {
                                double Kc = e.getValue();
                                return Kc * Pac.get(e.getKey());
                            }).sum() / numPac;
                    System.out.println("Found "+cnt.getAndIncrement()+")" + assignee + ": " + score);
                } else {
                    score = Double.MAX_VALUE;
                }
                return new Pair<>(assignee,score);
            }).sorted(Comparator.comparing(p->p.getSecond()))
                    //.limit(N2)
                    .collect(Collectors.toList());
            System.out.println("Num assignees found after stage 3: "+allAssignees3WithScores.size());
        }

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(new File("data/low_value_assignees.csv")))) {
            writer.write("Score,Assignee,Portfolio Size\n");
            for(Pair<String,Double> assigneeScorePair : allAssignees3WithScores) {
                String assignee = assigneeScorePair.getFirst();
                Double score = assigneeScorePair.getSecond();
                writer.write(score.toString()+","+assignee+","+finalAssigneeToPatentMap.get(assignee).size()+"\n");
            }
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.println("FINISHED!!!");
    }

    private static Map<String,Integer> buildIdxMap(List<String> entries) {
        return IntStream.range(0,entries.size()).parallel().mapToObj(i->new Pair<>(entries.get(i),i)).collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));
    }

    private static RealMatrix buildMatrix(List<String> allAssignees, List<String> allCpcCodes, Map<String,Set<String>> patentToCpcCodeMap, Map<String,Collection<String>> assigneeToPatentMap, Map<String,Integer> assigneeToIndexMap, Map<String,Integer> cpcCodeToIndexMap) {
        int numAssignees = allAssignees.size();
        int numCpcCodes = allCpcCodes.size();
        System.out.println("Matrix Dim: ["+numAssignees+"x"+numCpcCodes+"]");
        OpenMapBigRealMatrix matrix = new OpenMapBigRealMatrix(numAssignees,numCpcCodes);
        // data stream
       allAssignees.parallelStream().flatMap(assignee->{
            return assigneeToPatentMap.get(assignee).stream().map(patent->new Pair<>(assigneeToIndexMap.get(assignee),patent));
        }).forEach(pair->{
            Collection<String> cpcs = patentToCpcCodeMap.getOrDefault(pair.getSecond(),Collections.emptySet()).stream()
                    .map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc)
                    .filter(cpc->cpcCodeToIndexMap.containsKey(cpc))
                    .collect(Collectors.toSet());
            int assigneeIdx = pair.getFirst();
            cpcs.forEach(cpc->{
                int cpcIdx = cpcCodeToIndexMap.get(cpc);
                synchronized (matrix) {
                    matrix.addToEntry(assigneeIdx, cpcIdx, 1d);
                }
            });
        });
        return matrix;
    }

    private static Map<String,Double> computeRowWiseDensity(RealMatrix matrix, List<String> entries, Map<String,Integer> idxMap) {
        return computeDensity(matrix,entries,idxMap,true);
    }

    private static Map<String,Double> computeColumnWiseDensity(RealMatrix matrix, List<String> entries, Map<String,Integer> idxMap) {
        return computeDensity(matrix,entries,idxMap,false);
    }

    private static Map<String,Double> computeDensity(RealMatrix matrix, List<String> entries, Map<String,Integer> idxMap, boolean rowWise) {
        // get row sums
        int length = rowWise ? matrix.getRowDimension() : matrix.getColumnDimension();
        if(length!=entries.size()) throw new RuntimeException("Invalid list size.");

        double[] squaredSums = new double[length];
        double[] sumOfSquares = new double[length];
        IntStream.range(0,length).parallel().forEach(i->{
            double[] row = rowWise ? matrix.getRow(i) : matrix.getColumn(i);
            squaredSums[i] = DoubleStream.of(row).sum();
            squaredSums[i] = Math.pow(squaredSums[i],2);
            sumOfSquares[i] = DoubleStream.of(row).map(d->d*d).sum();
        });

        double[] scores = new double[length];
        IntStream.range(0,length).parallel().forEach(i->{
            double score = sumOfSquares[i]/squaredSums[i];
            if(Double.isNaN(score)) score = 0d;
            scores[i]=score;
        });
        return entries.parallelStream().collect(Collectors.toMap(keyword->keyword,keyword->scores[idxMap.get(keyword)]));
    }
}
