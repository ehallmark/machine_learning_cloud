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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/26/17.
 */
public class AssigneePortfolioAnalysis {
    private static final int minPortfolioSize = 1000;
    private static final int maxCpcLength = 10;
    private static final int N1 = 10000;
    private static final int N2 = 100;
    public static void main(String[] args) {
        List<String> allCpcCodes = Database.getClassCodes().parallelStream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct().collect(Collectors.toList());
        List<String> allAssignees;
        Map<String,Collection<String>> assigneeToPatentMap = new AssigneeToAssetsMap().getPatentDataMap();
        Map<String,Set<String>> patentToCpcCodeMap = new AssetToCPCMap().getPatentDataMap();
        {
            // step 1
            //  get all assignees with portfolio size > 1000

            allAssignees = Database.getAssignees().parallelStream().filter(assignee->{
                return assigneeToPatentMap.getOrDefault(assignee, Collections.emptyList()).size() >= minPortfolioSize;
            }).collect(Collectors.toList());
            System.out.println("Found assignees after stage 1: "+allAssignees.size());
        }

        List<String> allAssignees2;
        {
            // step 2
            //  calculate tech density per assignee
            RealMatrix matrix = buildMatrix(allAssignees,allCpcCodes,patentToCpcCodeMap,assigneeToPatentMap);
            System.out.println("Computing density per assignee");
            Map<String,Double> densityPerAssignee = computeRowWiseDensity(matrix, allAssignees);

            allAssignees2 = densityPerAssignee.entrySet().parallelStream().sorted((e1,e2)->{
                return e2.getValue().compareTo(e1.getValue());
            }).limit(N1).map(e->e.getKey()).collect(Collectors.toList());
            System.out.println("Num assignees found after stage 2: "+allAssignees2.size());
        }

        List<String> allAssignees3;
        {
            // step 3
            //  calculate assignee density per cpc
            allAssignees3 = allAssignees2.parallelStream().map(assignee->{
                System.out.println("Computing density per assignee");
                Collection<String> assigneePatents = assigneeToPatentMap.get(assignee);
                List<String> assigneeCpcCodesWithDups = assigneePatents.stream().flatMap(p->patentToCpcCodeMap.getOrDefault(p,Collections.emptySet()).stream())
                        .map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct()
                        .collect(Collectors.toList());

                Map<String,Long> Pac = assigneeCpcCodesWithDups.stream().collect(Collectors.groupingBy(e->e,Collectors.counting()));
                List<String> assigneeCpcCodes = assigneeCpcCodesWithDups.stream().distinct().collect(Collectors.toList());
                if(Pac.size()!=assigneeCpcCodes.size()) throw new RuntimeException("Error in assignee cpc codes size");
                
                RealMatrix matrix = buildMatrix(allAssignees, assigneeCpcCodes, patentToCpcCodeMap, assigneeToPatentMap);

                double score = assigneeCpcCodes.isEmpty() ? Integer.MAX_VALUE : computeColumnWiseDensity(matrix, assigneeCpcCodes).entrySet().stream()
                        .mapToDouble(e->{
                            double Kc = e.getValue();
                            return Kc * Pac.get(e.getKey());
                        }).sum()/assigneeCpcCodes.size();
                System.out.println("Score for assignee "+assignee+": "+score);
                return new Pair<>(assignee,score);
            }).sorted(Comparator.comparing(p->p.getSecond()))
                    .limit(N2).map(p->p.getFirst())
                    .collect(Collectors.toList());
            System.out.println("Num assignees found after stage 3: "+allAssignees3.size());
        }

        System.out.println("Assignees: "+String.join("; "+allAssignees3));
    }

    private static RealMatrix buildMatrix(List<String> allAssignees, List<String> allCpcCodes, Map<String,Set<String>> patentToCpcCodeMap, Map<String,Collection<String>> assigneeToPatentMap) {
        Map<String,Integer> assigneeToIndexMap = IntStream.of(0,allAssignees.size()).mapToObj(i->new Pair<>(allAssignees.get(i),i)).collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));
        Map<String,Integer> cpcCodeToIndexMap = IntStream.of(0,allCpcCodes.size()).mapToObj(i->new Pair<>(allCpcCodes.get(i),i)).collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));
        int numAssignees = allAssignees.size();
        int numCpcCodes = allCpcCodes.size();
        System.out.println("Matrix Dim: ["+numAssignees+"x"+numCpcCodes+"]");
        OpenMapBigRealMatrix matrix = new OpenMapBigRealMatrix(numAssignees,numCpcCodes);

        // data stream
        Stream<Pair<Integer,String>> allAssigneePatentPairs = allAssignees.parallelStream().flatMap(assignee->{
            return assigneeToPatentMap.get(assignee).stream().map(patent->new Pair<>(assigneeToIndexMap.get(assignee),patent));
        });

        allAssigneePatentPairs.forEach(pair->{
            Collection<String> cpcs = patentToCpcCodeMap.getOrDefault(pair.getSecond(),Collections.emptySet()).stream()
                    .map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc)
                    .collect(Collectors.toSet());
            int assigneeIdx = pair.getFirst();
            cpcs.forEach(cpc->{
                int cpcIdx = cpcCodeToIndexMap.get(cpc);
                matrix.addToEntry(assigneeIdx,cpcIdx, 1d);
            });
        });
        return matrix;
    }

    private static Map<String,Double> computeRowWiseDensity(RealMatrix matrix, List<String> entries) {
        return computeDensity(matrix,entries,true);
    }

    private static Map<String,Double> computeColumnWiseDensity(RealMatrix matrix, List<String> entries) {
        return computeDensity(matrix,entries,false);
    }

    private static Map<String,Double> computeDensity(RealMatrix matrix, List<String> entries, boolean rowWise) {
        // get row sums
        int length = rowWise ? matrix.getRowDimension() : matrix.getColumnDimension();
        Map<String,Integer> idxMap = IntStream.of(0,entries.size()).mapToObj(i->new Pair<>(entries.get(i),i)).collect(Collectors.toMap(p->p.getFirst(),p->p.getSecond()));
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
