package analysis;

import ca.pjer.ekmeans.EKmeans;
import com.google.common.util.concurrent.AtomicDouble;
import edu.stanford.nlp.util.Quadruple;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/11/16.
 */
public class KMeansCalculator {
    private List<Quadruple<double[][],List<Patent>,String,String>> expansions;
    private List<Patent> patentList;
    private String classString;
    private String scores;
    private static Set<String> alreadyTaken;

    public KMeansCalculator(String classString, String scores, List<Patent> patentList) {
        this.classString=classString;
        this.scores=scores;
        this.patentList=patentList;
    }

    public KMeansCalculator(String classString, Set<String> previous, String scores, double[][] points, List<Patent> patentList, Map<String,Pair<Float,INDArray>> vocab, int numData, int numClusters, int sampleSize, int iterations, int n, int numPredictions, boolean equal, org.nd4j.linalg.api.rng.Random rand, int depth) {
        this.classString=classString;
        this.alreadyTaken = previous == null ? new HashSet<>() : previous;
        this.scores=scores;
        int firstIdx = rand.nextInt(points.length);
        this.patentList=patentList;
        double[][] centroids = new double[numClusters][Constants.VECTOR_LENGTH];
        centroids[0] = points[firstIdx];
        List<double[]> prevClusters = new ArrayList<>();
        Set<Integer> prevClusterIndices = new HashSet<>();
        prevClusterIndices.add(firstIdx);
        prevClusters.add(centroids[0]);
        System.out.println("Calculating initial centroids...");
        try {
            int maxLength = Math.min(sampleSize*numClusters,numData);
            for (int i = 1; i < numClusters; i++) {
                AtomicInteger idxToAdd = new AtomicInteger(-1);
                AtomicDouble maxDistanceSoFar = new AtomicDouble(-1.0);
                for (int j = 0; j < maxLength; j++) {
                    if(prevClusterIndices.contains(j))continue;
                    double[] d = points[j];
                    double overallDistance = prevClusters.stream().collect(Collectors.summingDouble(v->SimilarPatentFinder.distance(v,d)));
                    if(overallDistance>maxDistanceSoFar.get()) {
                        maxDistanceSoFar.set(overallDistance);
                        idxToAdd.set(j);
                    }
                }
                if(idxToAdd.get() >= 0) {
                    prevClusterIndices.add(idxToAdd.get());
                    centroids[i] = points[idxToAdd.get()];
                    prevClusters.add(centroids[i]);
                }
            }
        } catch(Exception e) {
            throw new RuntimeException("Error while calculating initial centroids: \n"+e.toString());
        }

        System.out.println("Starting k means...");
        EKmeans eKmeans;
        try {
            eKmeans = new EKmeans(centroids, points);
            eKmeans.setEqual(equal);
            eKmeans.setIteration(iterations);
            //eKmeans.setDistanceFunction((d1,d2)->1.0d-Transforms.cosineSim(Nd4j.create(d1),Nd4j.create(d2)));
            eKmeans.run();

        } catch(Exception e) {
            throw new RuntimeException("Error running k means algorithm: \n"+e.toString());
        }
        System.out.println("Finished k means...");

        expansions = new ArrayList<>(numClusters);
        int[] assignments = eKmeans.getAssignments();
        assert assignments.length==numData : "K means has wrong number of data points!";


        List<List<Patent>> kMeansMap = new ArrayList<>();
        for(int i = 0; i < numClusters; i++) {
            kMeansMap.add(new ArrayList<>());
        }
        for(int i = 0; i < numData; i++) {
            kMeansMap.get(assignments[i]).add(patentList.get(i));
        }


        //AtomicInteger tech = new AtomicInteger(0);
        kMeansMap.forEach(subList->{
            //System.out.println("Calculating class: "+tech.get());
            if(subList.isEmpty())return;
            try {
                List<WordFrequencyPair<String,Float>> keywords = SimilarPatentFinder.predictMultipleKeywords(numPredictions+alreadyTaken.size(), vocab, subList, n, depth, sampleSize);
                double[][] subData = new double[subList.size()][Constants.VECTOR_LENGTH];
                for(int i = 0; i < subList.size(); i++) {
                    subData[i] = points[i];
                }
                StringJoiner classJoiner = new StringJoiner("|");
                StringJoiner scoreJoiner = new StringJoiner("|");
                int cnt = 0;
                for (int m = 0; m < keywords.size(); m++) {
                    if(alreadyTaken.contains(keywords.get(m).getFirst())) continue;
                    alreadyTaken.add(keywords.get(m).getFirst());
                    classJoiner.add(keywords.get(m).getFirst());
                    scoreJoiner.add(keywords.get(m).getSecond().toString());
                    cnt++;
                    if(cnt>= numPredictions) break;
                }
                expansions.add(new Quadruple<>(subData,subList,classJoiner.toString(),scoreJoiner.toString()));
            } catch(Exception ex) {
                throw new RuntimeException("Error predicting keywords for classification "+"\n"+ex.toString());
            }
        });

    }

    public Set<String> getPreviousTags() {
        return alreadyTaken;
    }

    public List<Quadruple<double[][],List<Patent>,String,String>> getExpansions() {
        return expansions;
    }

    public String getClassification() {
        return classString;
    }

    public String getScores() {
        return scores;
    }

    public List<Patent> getPatentList() {
        return patentList;
    }
}
