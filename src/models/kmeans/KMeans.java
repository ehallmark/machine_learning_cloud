package models.kmeans;

import data_pipeline.helpers.Function2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.DefaultRandom;
import org.nd4j.linalg.cpu.nativecpu.rng.CpuNativeRandom;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 11/27/2017.
 */
public class KMeans {
    private final int k;
    private List<Centroid> centroids;
    private List<DataPoint> dataPoints;
    private double reclusterError;
    private double reassignmentError;
    private Function2<INDArray,INDArray,Double> distanceFunction;
    public KMeans(int k, Function2<INDArray,INDArray,Double> distanceFunction) {
        this.k=k;
        this.distanceFunction=distanceFunction;
    }

    public List<Set<String>> getClusters() {
        List<Set<String>> clusters = new ArrayList<>();
        centroids.forEach(centroid->{
            Set<String> points = new HashSet<>();
            centroid.dataPoints.forEach(point->{
                points.add(point.name);
            });
        });
        return clusters;
    }

    public void fit(Map<String,INDArray> dataMap, int nEpochs, boolean forceReinitCentroids) {
        this.dataPoints = dataMap.entrySet().parallelStream().map(e->new DataPoint(e.getKey(),e.getValue())).collect(Collectors.toList());
        if(this.centroids==null||forceReinitCentroids) {
            this.centroids = initializeCentroids();
        }

        for(int n = 0; n < nEpochs; n++) {
            System.out.println("Starting epoch: "+(n+1));

            this.reassignmentError = this.reassignDataToClusters();
            System.out.println("Reassignment error: "+reassignmentError);

            this.reclusterError = this.recomputeClusterAverages();
            System.out.println("Recluster error: "+reclusterError);
        }
    }

    private List<Centroid> initializeCentroids() {
        System.out.println("Building centroids...");

        Random random = new Random(6342);
        List<Centroid> centroids = Collections.synchronizedList(new ArrayList<>());
        for(int i = 0; i < k; i++) {
            INDArray dataPoint = dataPoints.get(random.nextInt(dataPoints.size())).dataPoint;
            INDArray rand = Nd4j.randn(dataPoint.shape()).div(10);
            centroids.add(new Centroid(dataPoint.add(rand)));
        }
        return centroids;
    }

    private double recomputeClusterAverages() {
        return centroids.parallelStream().mapToDouble(Centroid::recomputeMeanAndReturnError).average().orElse(Double.NaN);
    }

    private double reassignDataToClusters() {
        return dataPoints.parallelStream().mapToDouble(dataPoint->{
            Centroid centroid = findClosestCentroid(dataPoint);
            if(dataPoint.centroid!=null&&!dataPoint.centroid.equals(centroid)) {
                dataPoint.centroid.dataPoints.remove(dataPoint);
            }
            centroid.dataPoints.add(dataPoint);
            dataPoint.centroid=centroid;
            return distanceFunction.apply(dataPoint.dataPoint,centroid.mean);
        }).average().orElse(Double.NaN);
    }

    private Centroid findClosestCentroid(DataPoint dataPoint) {
        Centroid bestChoice = null;
        double bestDistance = Double.MAX_VALUE;
        for(Centroid centroid : centroids) {
            double distance = distanceFunction.apply(dataPoint.dataPoint,centroid.mean);
            if(distance < bestDistance) {
                bestChoice = centroid;
                bestDistance=distance;
            }
        }
        if(bestChoice==null) throw new RuntimeException("Unable to find closest centroid... Something must be wrong.");
        return bestChoice;
    }

    @Override
    public String toString() {
        return String.join("\n", centroids.stream().map(centroid->centroid.toString()).collect(Collectors.toList()));
    }

    private class DataPoint {
        private String name;
        private INDArray dataPoint;
        private Centroid centroid;
        DataPoint(String name, INDArray dataPoint) {
            this.name=name;
            this.dataPoint=dataPoint;
        }

        @Override
        public String toString() {
            return "DataPoint["+name+"]";
        }
    }

    private class Centroid {
        private INDArray mean;
        private Set<DataPoint> dataPoints;
        Centroid(INDArray mean) {
            this.mean=mean;
            this.dataPoints= Collections.synchronizedSet(new HashSet<>());
        }

        private double recomputeMeanAndReturnError() {
            INDArray meanTminus1 = mean;
            if(!dataPoints.isEmpty()) {
                mean = Nd4j.vstack(dataPoints.stream().map(dp -> dp.dataPoint).collect(Collectors.toList())).mean(0);
            }
            return distanceFunction.apply(meanTminus1,mean);
        }

        @Override
        public String toString() {
            return "Centroid[mean="+mean.toString()+", datapoints=("+String.join(",",dataPoints.stream().map(dp->dp.toString()).collect(Collectors.toList()))+")]";
        }
    }


    public static void main(String[] args) {
        // test
        KMeans kMeans = new KMeans(3, DistanceFunctions.L2_DISTANCE_FUNCTION);

        Map<String,INDArray> dataMap = new HashMap<>();
        dataMap.put("v1", Nd4j.create(new double[]{1.,22.,3.}));
        dataMap.put("v2", Nd4j.create(new double[]{0.,2.,1.}));
        dataMap.put("v3", Nd4j.create(new double[]{1.,2.,3.}));
        dataMap.put("v4", Nd4j.create(new double[]{2.,1.,3.5}));
        dataMap.put("v5", Nd4j.create(new double[]{-1.,-2.5,-3.}));
        dataMap.put("v6", Nd4j.create(new double[]{-1.,-2.,-3.}));

        kMeans.fit(dataMap, 20, false);

        System.out.println(kMeans.toString());
    }
}
