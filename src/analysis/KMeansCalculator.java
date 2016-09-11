package analysis;

import ca.pjer.ekmeans.EKmeans;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AtomicDouble;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.*;
import seeding.Constants;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/11/16.
 */
public class KMeansCalculator {
    private Map<Integer,List<WordFrequencyPair<String,Float>>> cache;
    private Map<String,List<WordFrequencyPair<String,Float>>> patentClass;
    private List<Pair<double[][],List<Patent>>> expansions;
    private List<Patent> patentList;

    public KMeansCalculator(double[][] points, List<Patent> patentList, Map<String,Pair<Float,INDArray>> vocab, int numData, int numClusters, int sampleSize, int iterations, int n, int numPredictions, boolean equal, org.nd4j.linalg.api.rng.Random rand) {
        int firstIdx = rand.nextInt(points.length);
        patentClass = new HashMap<>();
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

        for(List<Patent> subList : kMeansMap)
        {
            if(subList==null||subList.isEmpty()) continue;
            double[][] subData = new double[subList.size()][Constants.VECTOR_LENGTH];
            for(int i = 0; i < subList.size(); i++) {
                subData[i] = points[i];
            }
            expansions.add(new Pair<>(subData,subList));
        }


        // compute best phrases for each cluster
        cache = new HashMap<>();
        //AtomicInteger tech = new AtomicInteger(0);
        kMeansMap.forEach(subList->{
            //System.out.println("Calculating class: "+tech.get());
            if(subList.isEmpty())return;
            try {
                List<WordFrequencyPair<String,Float>> pair = SimilarPatentFinder.predictMultipleKeywords(numPredictions, vocab, subList, n, sampleSize);
                subList.forEach(patent->{
                    patentClass.put(patent.getName(),pair);
                });
            } catch(Exception ex) {
                throw new RuntimeException("Error predicting keywords for classification "+"\n"+ex.toString());
            }
        });

        /*tech.set(0);
        results = kMeansMap.stream().map(subList->{
            int idx = tech.getAndIncrement();
            try {
                if(subList.isEmpty())return null;
                List<WordFrequencyPair<String,Float>> predictions = cache.get(idx);
                if(predictions==null||predictions.isEmpty()) return null;
                return Maps.immutableEntry(String.join("|",predictions.stream().map(p->p.getFirst()).collect(Collectors.toSet())),new Pair<>(predictions.stream().collect(Collectors.averagingDouble(p->p.getSecond())),subList.stream().map(p->p.getName()).collect(Collectors.toSet())));
            } catch(Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }).filter(p->p!=null).sorted((e2,e1)->e1.getValue().getFirst().compareTo(e2.getValue().getFirst())).collect(Collectors.toList());
        */

    }

    public List<Pair<double[][],List<Patent>>> getExpansions() {
        return expansions;
    }

    public Map<Integer,List<WordFrequencyPair<String,Float>>> getCache() {
        return cache;
    }

    public Map<String,List<WordFrequencyPair<String,Float>>> getResults() {
        return patentClass;
    }

    public List<Patent> getPatentList() {
        return patentList;
    }
}
