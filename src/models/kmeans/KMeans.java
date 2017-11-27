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
    private Function2<INDArray,INDArray,INDArray> distanceFunction;
    public KMeans(int k, Function2<INDArray,INDArray,INDArray> distanceFunction) {
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
        INDArray centroidsMatrix = Nd4j.vstack(centroids.stream().map(centroid->centroid.mean).collect(Collectors.toList()));
        return dataPoints.parallelStream().mapToDouble(dataPoint->{
            Centroid centroid = findClosestCentroid(dataPoint,centroidsMatrix);
            if(dataPoint.centroid!=null&&!dataPoint.centroid.equals(centroid)) {
                dataPoint.centroid.dataPoints.remove(dataPoint);
            }
            centroid.dataPoints.add(dataPoint);
            dataPoint.centroid=centroid;
            return distanceFunction.apply(dataPoint.dataPoint,centroid.mean).getDouble(0);
        }).average().orElse(Double.NaN);
    }

    private Centroid findClosestCentroid(DataPoint dataPoint, INDArray centroidsMatrix) {
        INDArray distanceMatrix = distanceFunction.apply(dataPoint.dataPoint.broadcast(centroidsMatrix.shape()),centroidsMatrix);
        int bestChoice = Nd4j.argMax(distanceMatrix,0).getInt(0);
        return centroids.get(bestChoice);
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
            return distanceFunction.apply(meanTminus1,mean).getDouble(0);
        }

        @Override
        public String toString() {
            return "Centroid[mean="+mean.toString()+", datapoints=("+String.join(",",dataPoints.stream().map(dp->dp.toString()).collect(Collectors.toList()))+")]";
        }
    }


    public static void main(String[] args) {
        // test
        KMeans kMeans = new KMeans(10, DistanceFunctions.COSINE_DISTANCE_FUNCTION);

        Map<String,INDArray> dataMap = new HashMap<>();
        Random rand = new Random();
        for(int i = 0; i < 100; i++) {
            dataMap.put("v"+i, Nd4j.create(new double[]{rand.nextDouble(),rand.nextDouble(),rand.nextDouble()}));
        }

        kMeans.fit(dataMap, 20, false);

        System.out.println(kMeans.toString());
    }
}
