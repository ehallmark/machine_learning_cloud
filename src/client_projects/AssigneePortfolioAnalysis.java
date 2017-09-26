package client_projects;

import models.keyphrase_prediction.scorers.TermhoodScorer;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
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
    private static final int minPortfolioSize = 250;
    private static final int maxCpcLength = 10;
    private static final int N1 = 1000;
    private static final int N2 = 50;
    public static void main(String[] args) {
        List<String> allCpcCodes = Database.getClassCodes().parallelStream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct().collect(Collectors.toList());
        List<String> allAssignees = new ArrayList<>(Database.getAssignees());
        Map<String,Set<String>> patentToCpcCodeMap = new AssetToCPCMap().getPatentDataMap();
        Map<String,Collection<String>> assigneeToPatentMap;
        Map<String,Integer> allCpcCodeToIndexMap = buildIdxMap(allCpcCodes);
        Map<String,AtomicInteger> cpcToTotalCountMap = Collections.synchronizedMap(new HashMap<>());
        patentToCpcCodeMap.entrySet().parallelStream().forEach(e->{
            e.getValue().stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).collect(Collectors.toList()).forEach(cpc->{
                cpcToTotalCountMap.putIfAbsent(cpc,new AtomicInteger(0));
                cpcToTotalCountMap.get(cpc).getAndIncrement();
            });
        });
        List<String> allAssignees1;
        {
            // step 1
            //  get all assignees with portfolio size > 1000
            assigneeToPatentMap = new AssigneeToAssetsMap().getPatentDataMap();
            allAssignees1 = allAssignees.parallelStream().filter(assignee->{
                return assigneeToPatentMap.getOrDefault(assignee, Collections.emptyList()).size() >= minPortfolioSize;
            }).collect(Collectors.toList());
            System.out.println("Found assignees after stage 1: "+allAssignees1.size());

        }

        List<String> allAssignees2;
        {
            // step 2
            //  calculate tech density per assignee
            Map<String,Integer> assignee1ToIndexMap = buildIdxMap(allAssignees1);
            RealMatrix matrix = buildMatrix(allAssignees1,allCpcCodes,patentToCpcCodeMap,assigneeToPatentMap, assignee1ToIndexMap, allCpcCodeToIndexMap);
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
                Collection<String> assigneePatents = assigneeToPatentMap.get(assignee);
                List<String> assigneeCpcCodesWithDups = assigneePatents.parallelStream().flatMap(p->patentToCpcCodeMap.getOrDefault(p,Collections.emptySet()).stream())
                        .map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc)
                        .collect(Collectors.toList());
                Map<String,Long> Pac = assigneeCpcCodesWithDups.parallelStream().collect(Collectors.groupingBy(e->e,Collectors.counting()));
                List<String> assigneeCpcCodes = assigneeCpcCodesWithDups.parallelStream().distinct().collect(Collectors.toList());
                if(Pac.size()!=assigneeCpcCodes.size()) throw new RuntimeException("Error in assignee cpc codes size");

                double score;
                if(assigneeCpcCodes.size()>0) {
                    INDArray cpcVec = Nd4j.create(IntStream.range(0,assigneeCpcCodes.size()).mapToDouble(i->Pac.get(assigneeCpcCodes.get(i)).doubleValue()).toArray());
                    double cpcSum = cpcVec.sumNumber().doubleValue();
                    double[] cpcArray = cpcVec.data().asDouble();
                    INDArray globalCpcVec = Nd4j.create(IntStream.range(0,assigneeCpcCodes.size()).mapToDouble(i->cpcToTotalCountMap.get(assigneeCpcCodes.get(i)).doubleValue()).toArray());
                    double[] percentages = cpcVec.div(globalCpcVec.addi(1d)).data().asDouble();
                    score = IntStream.range(0,assigneeCpcCodes.size()).mapToDouble(i->percentages[i]*cpcArray[i]).sum()/cpcSum;
                    System.out.println("Finished "+cnt.getAndIncrement());
                    System.out.println("Coverage Per Patent for assignee " + assignee + ": " + score);
                } else {
                    score = Double.MAX_VALUE;
                }
                return new Pair<>(assignee,score);
            }).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond()))
                    //.limit(N2)
                    .collect(Collectors.toList());
            System.out.println("Num assignees found after stage 3: "+allAssignees3WithScores.size());
        }

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(new File("data/low_value_assignees.csv")))) {
            writer.write("Score,Assignee,Portfolio Size\n");
            for(Pair<String,Double> assigneeScorePair : allAssignees3WithScores) {
                String assignee = assigneeScorePair.getFirst();
                Double score = assigneeScorePair.getSecond();
                writer.write(score.toString()+","+assignee+","+assigneeToPatentMap.get(assignee).size()+"\n");
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
