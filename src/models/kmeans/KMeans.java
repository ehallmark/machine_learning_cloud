package models.kmeans;

import data_pipeline.helpers.Function2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.DefaultRandom;
import org.nd4j.linalg.cpu.nativecpu.rng.CpuNativeRandom;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Created by Evan on 11/27/2017.
 */
public class KMeans {
    private final int k;
    private List<Centroid> centroids;
    private List<DataPoint> dataPoints;
    private double reclusterError;
    private double reassignmentError;
    private INDArray centroidMatrix;
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
        /* k-means++ algorithm
            1- Choose one center uniformly at random from among the data points.
            2- For each data point x, compute D(x), the distance between x and the nearest center that has already been chosen.
            3- Choose one new data point at random as a new center, using a weighted probability distribution where a point x is chosen with probability proportional to D(x)2.
            4- Repeat Steps 2 and 3 until k centers have been chosen.
         */
        int vectorSize = dataPoints.get(0).dataPoint.length();
        Random random = new Random(6342);
        this.centroidMatrix = Nd4j.create(k,vectorSize);
        this.centroids = Collections.synchronizedList(new ArrayList<>());
        List<DataPoint> dataPointsRemaining = new ArrayList<>(this.dataPoints);

        // step 1: pick one center uniformly at random
        DataPoint dataPoint = dataPointsRemaining.remove(random.nextInt(dataPointsRemaining.size()));
        int n = this.centroids.size();
        this.centroidMatrix.putRow(n,dataPoint.dataPoint.dup());
        this.centroids.add(new Centroid(this.centroidMatrix.getRow(n)));
        while(this.centroids.size()<k) {
            INDArray validCentroidMatrix = this.centroidMatrix.get(NDArrayIndex.interval(0,this.centroids.size()),NDArrayIndex.all());
            dataPointsRemaining.parallelStream().forEach(remaining->remaining.dist=distanceFunction.apply(findClosestCentroid(remaining,validCentroidMatrix).mean,remaining.dataPoint).getDouble(0));

            // step 2: compute distances
            double[] probabilities = dataPointsRemaining.stream().mapToDouble(d->Math.pow(d.dist,2)).toArray();
            double sum = DoubleStream.of(probabilities).parallel().sum();
            double rand = random.nextDouble()*sum;
            double curr = 0d;
            for(int i = 0; i < probabilities.length; i++) {
                curr += probabilities[i];
                if(curr >= rand) {
                    System.out.print("-");
                    // done
                    dataPoint = dataPointsRemaining.remove(i);
                    n = this.centroids.size();
                    this.centroidMatrix.putRow(n,dataPoint.dataPoint.dup());
                    this.centroids.add(new Centroid(this.centroidMatrix.getRow(n)));
                    break;
                }
            }
        }
        return this.centroids;
    }

    private double recomputeClusterAverages() {
        System.gc();
        return this.centroids.parallelStream().mapToDouble(Centroid::recomputeMeanAndReturnError).average().orElse(Double.NaN);
    }

    private double reassignDataToClusters() {
        AtomicInteger cnt = new AtomicInteger(0);
        System.gc();
        return dataPoints.parallelStream().mapToDouble(dataPoint->{
            Centroid centroid = findClosestCentroid(dataPoint,this.centroidMatrix);
            if(dataPoint.centroid!=null&&!dataPoint.centroid.equals(centroid)) {
                dataPoint.centroid.dataPoints.remove(dataPoint);
            }
            centroid.dataPoints.add(dataPoint);
            dataPoint.centroid=centroid;
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.print("-");
                System.gc();
            }
            return distanceFunction.apply(dataPoint.dataPoint,centroid.mean).getDouble(0);
        }).average().orElse(Double.NaN);
    }

    private Centroid findClosestCentroid(DataPoint dataPoint, INDArray centroidMatrix) {
        INDArray distanceMatrix = distanceFunction.apply(dataPoint.dataPoint,centroidMatrix);
        int bestChoice = Nd4j.argMax(distanceMatrix.negi(),0).getInt(0);
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
        private double dist;
        DataPoint(String name, INDArray dataPoint) {
            this.name=name;
            this.dataPoint=dataPoint;
        }

        @Override
        public String toString() {
            return "DataPoint["+name+"="+dataPoint.toString()+"]";
        }
    }

    private class Centroid {
        private INDArray mean;
        private Set<DataPoint> dataPoints;
        Centroid(INDArray mean) {
            this.mean=mean;
            this.dataPoints = Collections.synchronizedSet(new HashSet<>());
        }

        private double recomputeMeanAndReturnError() {
            INDArray meanTminus1 = mean.dup();
            if(!dataPoints.isEmpty()) {
                mean.assign(Nd4j.vstack(dataPoints.stream().map(dp -> dp.dataPoint).collect(Collectors.toList())).mean(0));
                System.gc();
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
            dataMap.put("v"+i, Nd4j.create(new double[]{rand.nextDouble(),rand.nextDouble()}));
        }

        kMeans.fit(dataMap, 200, false);

        System.out.println(kMeans.toString());
    }
}
