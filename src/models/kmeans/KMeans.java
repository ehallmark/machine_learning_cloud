package models.kmeans;

import data_pipeline.helpers.Function2;
import lombok.Getter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Created by Evan on 11/27/2017.
 */
public class KMeans {
    private static final int DEFAULT_MAX_NUM_EPOCHS_FOR_CENTROID_INIT = 100;
    private static final double EPSILON = 0.0000001;
    private final int k;
    private final int l;
    @Getter
    private List<Centroid> centroids;
    private List<DataPoint> dataPoints;
    private double error;
    private double avgError;
    private double avgErrorAlpha;
    private INDArray centroidMatrix;
    private Function2<INDArray,INDArray,INDArray> distanceFunction;
    private boolean largeDataset;
    @Getter
    private boolean converged;
    private int maxNumEpochsForCentroidInit;
    public KMeans(int k, int l, Function2<INDArray,INDArray,INDArray> distanceFunction, boolean largeDataset, int maxNumEpochsForCentroidInit) {
        this.k=k;
        this.l=l;
        this.maxNumEpochsForCentroidInit=maxNumEpochsForCentroidInit;
        this.distanceFunction=distanceFunction;
        this.largeDataset=largeDataset;
        this.avgError=0d;
        this.converged=false;
        this.avgErrorAlpha = 0.8;
    }

    public KMeans(int k, Function2<INDArray,INDArray,INDArray> distanceFunction, boolean largeDataset, int maxNumEpochsForCentroidInit) {
        this(k,2*k,distanceFunction,largeDataset,maxNumEpochsForCentroidInit);
    }

    public KMeans(int k, int l, Function2<INDArray,INDArray,INDArray> distanceFunction) {
        this(k,l,distanceFunction,true,DEFAULT_MAX_NUM_EPOCHS_FOR_CENTROID_INIT);
    }

    public KMeans(int k, Function2<INDArray,INDArray,INDArray> distanceFunction) {
        this(k,distanceFunction,true,DEFAULT_MAX_NUM_EPOCHS_FOR_CENTROID_INIT);
    }


    public KMeans(int k, Function2<INDArray,INDArray,INDArray> distanceFunction, boolean largeDataset) {
        this(k,distanceFunction,largeDataset,DEFAULT_MAX_NUM_EPOCHS_FOR_CENTROID_INIT);
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

        Double lastError = null;
        for(int n = 0; n < nEpochs; n++) {
            System.out.println("Starting epoch: "+(n+1));

            // reassign points to nearest cluster
            this.error = this.reassignDataToClusters();
            System.out.println("Current error: "+error);

            // compute average error
            this.avgError = (1d-avgErrorAlpha)*this.avgError + avgErrorAlpha*this.error;
            double unbiasedAvgError = this.avgError / (1d - Math.pow(avgErrorAlpha,n+1));
            System.out.println("Avg error: "+unbiasedAvgError);

            // recenter centroids
            this.recomputeClusterAverages();

            // check convergence
            if(lastError!=null&&Math.abs(error-lastError)<EPSILON) {
                this.converged=true;
                break;
            }

            lastError = this.error;
        }
        System.out.println("Converged: "+isConverged());
    }

    private List<Centroid> initializeCentroids() {
        System.out.println("Building centroids...");

        Random random = new Random(6342);
        this.centroids = Collections.synchronizedList(new ArrayList<>());
        List<DataPoint> dataPointsRemaining = Collections.synchronizedList(new ArrayList<>(this.dataPoints));

        // step 1: pick one center uniformly at random
        DataPoint dataPoint = dataPointsRemaining.remove(random.nextInt(dataPointsRemaining.size()));
        INDArray centroid0 = dataPoint.dataPoint.dup();
        this.centroids.add(new Centroid(centroid0));
        this.centroidMatrix = centroid0;

        if(largeDataset) {
            this.error = this.reassignDataToClusters();
            long iterations = Math.round(Math.log(this.error));
            for (int iteration = 0; iteration < iterations; iteration++) {
                System.out.println("Finding centroid approximation: "+(iteration+1) + " / "+iterations);
                // step 2: compute distances
                List<Centroid> newCentroids = Collections.synchronizedList(new ArrayList<>());
                List<Integer> indicesToRemove = Collections.synchronizedList(new ArrayList<>());
                IntStream.range(0, dataPointsRemaining.size()).parallel().forEach(i -> {
                    double prob = (dataPointsRemaining.get(i).squareDist * l) / this.error;
                    double rand = random.nextDouble();
                    if (prob > rand) {
                        // add sample
                        INDArray centroid = dataPointsRemaining.get(i).dataPoint.dup();
                        newCentroids.add(new Centroid(centroid));
                        indicesToRemove.add(i);
                    }
                });
                newCentroids.forEach(centroid -> {
                    this.centroids.add(centroid);
                    this.centroidMatrix = Nd4j.vstack(centroidMatrix, centroid.mean);
                });
                indicesToRemove.stream().sorted(Comparator.reverseOrder()).forEach(dataPointsRemaining::remove);
                this.error = this.reassignDataToClusters();
            }
            // compute w
            int[] w = new int[this.centroids.size()];
            Arrays.fill(w, 0);
            for (int i = 0; i < centroids.size(); i++) {
                w[i] = centroids.get(i).dataPoints.size();
            }
            Map<String,INDArray> centroidMap = Collections.synchronizedMap(new HashMap<>());
            AtomicInteger idx = new AtomicInteger(0);
            centroids.forEach(centroid->centroidMap.put(String.valueOf(idx.getAndIncrement()),centroid.mean));
            System.out.println("Recentering "+centroidMap.size()+" centroids...");
            KMeans child = new KMeans(k,distanceFunction,false);
            child.fit(centroidMap,maxNumEpochsForCentroidInit,true);
            List<Centroid> newCentroids = child.getCentroids();
            this.centroids.clear();
            int vectorSize = dataPoints.get(0).dataPoint.length();
            this.centroidMatrix=Nd4j.create(k,vectorSize);
            idx.set(0);
            newCentroids.forEach(centroid->{
                centroidMatrix.putRow(idx.getAndIncrement(),centroid.mean);
                centroids.add(centroid);
            });

        } else {
             /* k-means++ algorithm
                1- Choose one center uniformly at random from among the data points.
                2- For each data point x, compute D(x), the distance between x and the nearest center that has already been chosen.
                3- Choose one new data point at random as a new center, using a weighted probability distribution where a point x is chosen with probability proportional to D(x)2.
                4- Repeat Steps 2 and 3 until k centers have been chosen.
             */
            // recluster the weighted points in C into k clusters
            while(this.centroids.size()<k&&!dataPointsRemaining.isEmpty()) {
                System.out.print("-");
                this.error = reassignDataToClusters();

                // step 2: compute distances
                double[] probabilities = dataPointsRemaining.stream().mapToDouble(d->d.squareDist).toArray();
                double rand = random.nextDouble()*error;
                double curr = 0d;
                for(int i = 0; i < probabilities.length; i++) {
                    curr += probabilities[i];
                    if(curr >= rand) {
                        // done
                        dataPoint = dataPointsRemaining.remove(i);
                        INDArray centroid = dataPoint.dataPoint.dup();
                        this.centroids.add(new Centroid(centroid));
                        this.centroidMatrix = Nd4j.vstack(this.centroidMatrix,centroid);
                        break;
                    }
                }
            }
        }
        return this.centroids;
    }

    private void recomputeClusterAverages() {
        System.gc();
        centroids.parallelStream().forEach(Centroid::recomputeMean);
        // rebuild global centroid matrix
        this.centroidMatrix = Nd4j.vstack(centroids.stream().map(centroid->centroid.mean).collect(Collectors.toList()));
    }

    private double reassignDataToClusters() {
        AtomicInteger cnt = new AtomicInteger(0);
        System.gc();
        return dataPoints.parallelStream().mapToDouble(dataPoint->{
            Centroid centroid = centroids.size()==1?centroids.get(0):findClosestCentroid(dataPoint);
            if(centroids.size()==1) dataPoint.squareDist=distanceFunction.apply(dataPoint.dataPoint,centroid.mean).getDouble(0);
            if(dataPoint.centroid!=null&&!dataPoint.centroid.equals(centroid)) {
                dataPoint.centroid.dataPoints.remove(dataPoint);
            }
            centroid.dataPoints.add(dataPoint);
            dataPoint.centroid=centroid;
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.print("-");
                System.gc();
            }
             return dataPoint.squareDist;
        }).sum();
    }

    private Centroid findClosestCentroid(DataPoint dataPoint) {
        INDArray distanceMatrix = distanceFunction.apply(dataPoint.dataPoint,centroidMatrix);
        int bestChoice = Nd4j.argMax(distanceMatrix.neg(),0).getInt(0);
        dataPoint.squareDist = Math.pow(distanceMatrix.getDouble(bestChoice),2);
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
        private double squareDist;
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

        private void recomputeMean() {
            if(!dataPoints.isEmpty()) {
                mean = Nd4j.vstack(dataPoints.stream().map(dp -> dp.dataPoint).collect(Collectors.toList())).mean(0);
            }
        }

        @Override
        public String toString() {
            return "Centroid[mean="+mean.toString()+", datapoints=("+String.join(",",dataPoints.stream().map(dp->dp.toString()).collect(Collectors.toList()))+")]";
        }
    }


    public static void main(String[] args) {
        // test
        KMeans kMeans = new KMeans(10, DistanceFunctions.L2_DISTANCE_FUNCTION);

        Map<String,INDArray> dataMap = new HashMap<>();
        Random rand = new Random();
        for(int i = 0; i < 200; i++) {
            dataMap.put("v"+i, Nd4j.create(new double[]{rand.nextDouble(),rand.nextDouble()}));
        }

        kMeans.fit(dataMap, 20, false);

        System.out.println(kMeans.toString());
    }
}
