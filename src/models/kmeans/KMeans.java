package models.kmeans;

import data_pipeline.helpers.Function2;
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
    private final int k;
    private List<Centroid> centroids;
    private List<DataPoint> dataPoints;
    private double error;
    private INDArray centroidMatrix;
    private Function2<INDArray,INDArray,INDArray> distanceFunction;
    private boolean parallel;
    public KMeans(int k, Function2<INDArray,INDArray,INDArray> distanceFunction, boolean parallel) {
        this.k=k;
        this.distanceFunction=distanceFunction;
        this.parallel=parallel;
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

            this.error = this.reassignDataToClusters();
            System.out.println("Reassignment error: "+error);

            this.recomputeClusterAverages();
        }
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

        if(parallel) {
            long iterations = Math.round(Math.log(this.error));
            int l = 2; // sampling factor
            for (int iteration = 0; iteration < iterations; iteration++) {
                // step 2: compute distances
                this.error = this.reassignDataToClusters();
                List<Centroid> newCentroids = Collections.synchronizedList(new ArrayList<>());
                SortedSet<Integer> indicesToRemove = Collections.synchronizedSortedSet(new TreeSet<>(Comparator.reverseOrder()));
                IntStream.range(0, dataPointsRemaining.size()).parallel().forEach(i -> {
                    double prob = dataPointsRemaining.get(i).squareDist;
                    double rand = random.nextDouble();
                    if (prob / this.error > rand / l) {
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
                indicesToRemove.forEach(dataPointsRemaining::remove);
            }
            // compute w
            this.error = this.reassignDataToClusters();
            int[] w = new int[this.centroids.size()];
            Arrays.fill(w, 0);
            for (int i = 0; i < centroids.size(); i++) {
                w[i] = centroids.get(i).dataPoints.size();
            }
        } else {
            /* k-means++ algorithm
                1- Choose one center uniformly at random from among the data points.
                2- For each data point x, compute D(x), the distance between x and the nearest center that has already been chosen.
                3- Choose one new data point at random as a new center, using a weighted probability distribution where a point x is chosen with probability proportional to D(x)2.
                4- Repeat Steps 2 and 3 until k centers have been chosen.
             */
        }
        // recluster the weighted points in C into k clusters
        /*while(this.centroids.size()<k) {
            System.out.print("-");
            dataPointsRemaining.parallelStream().forEach(this::findClosestCentroid);

            // step 2: compute distances
            double[] probabilities = dataPointsRemaining.stream().mapToDouble(d->Math.pow(d.dist,2)).toArray();
            double sum = DoubleStream.of(probabilities).parallel().sum();
            double rand = random.nextDouble()*sum;
            double curr = 0d;
            for(int i = 0; i < probabilities.length; i++) {
                curr += probabilities[i];
                if(curr >= rand) {
                    System.out.print("_");
                    // done
                    dataPoint = dataPointsRemaining.remove(i);
                    centroid = dataPoint.dataPoint.dup();
                    this.centroids.add(new Centroid(centroid));
                    this.centroidMatrix = Nd4j.vstack(this.centroidMatrix,centroid);
                    break;
                }
            }
        }*/
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
        KMeans kMeans = new KMeans(10, DistanceFunctions.COSINE_DISTANCE_FUNCTION, true);

        Map<String,INDArray> dataMap = new HashMap<>();
        Random rand = new Random();
        for(int i = 0; i < 100; i++) {
            dataMap.put("v"+i, Nd4j.create(new double[]{rand.nextDouble(),rand.nextDouble()}));
        }

        kMeans.fit(dataMap, 200, false);

        System.out.println(kMeans.toString());
    }
}
