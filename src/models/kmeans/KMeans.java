package models.kmeans;

import data_pipeline.helpers.Function2;
import lombok.Getter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Evan on 11/27/2017.
 */
public class KMeans {
    private static final int DEFAULT_MAX_NUM_EPOCHS_FOR_CENTROID_INIT = 100;
    private static final double EPSILON = 0.0000001;
    private final int k;
    private final double l;
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
    public KMeans(int k, double l, Function2<INDArray,INDArray,INDArray> distanceFunction, boolean largeDataset, int maxNumEpochsForCentroidInit) {
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

    public KMeans(int k, double l, Function2<INDArray,INDArray,INDArray> distanceFunction) {
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
            clusters.add(points);
        });
        return clusters;
    }

    public void fit(Map<String,INDArray> dataMap, int nEpochs, boolean forceReinitCentroids) {
        this.fit(dataMap,null,nEpochs,forceReinitCentroids);
    }

    public void fit(Map<String,INDArray> dataMap, Map<String,Double> sampleProbabilities, int nEpochs, boolean forceReinitCentroids) {
        this.dataPoints = dataMap.entrySet().stream().map(e->new DataPoint(e.getKey(),e.getValue())).collect(Collectors.toList());
        if(this.centroids==null||forceReinitCentroids) {
            this.centroids = initializeCentroids(sampleProbabilities);
        }

        Double lastError = null;
        for(int n = 0; n < nEpochs; n++) {
            System.out.println("Starting epoch: "+(n+1));

            // reassign points to nearest cluster
            this.error = this.reassignDataToClusters(true);
            System.out.println("Current error: "+error);

            // compute average error
            this.avgError = (1d-avgErrorAlpha)*this.error + avgErrorAlpha*this.avgError;
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

    private static DataPoint pickRandomly(List<DataPoint> dataPoints, Map<String,Double> sampleProbabilities, Random random) {
        if(sampleProbabilities==null) {
            return dataPoints.remove(random.nextInt(dataPoints.size()));
        } else {
            double curr = 0d;
            double rand = random.nextDouble();
            DataPoint ret = null;
            for(DataPoint dataPoint : dataPoints) {
                curr+=sampleProbabilities.get(dataPoint.name);
                if(curr >= rand) {
                    ret = dataPoint;
                    break;
                }
            }
            return ret;
        }
    }

    private List<Centroid> initializeCentroids(Map<String,Double> sampleProbabilities) {
        System.out.println("Building centroids...");

        Random random = new Random(6342);
        this.centroids = Collections.synchronizedList(new ArrayList<>());
        List<DataPoint> dataPointsRemaining = Collections.synchronizedList(new ArrayList<>(this.dataPoints));

        // step 1: pick one center uniformly at random
        DataPoint dataPoint0 = pickRandomly(dataPointsRemaining,sampleProbabilities,random);
        INDArray centroid0 = dataPoint0.dataPoint.dup();
        this.centroids.add(new Centroid(centroid0));
        this.centroidMatrix = centroid0;

        if(largeDataset) {
            this.error = this.reassignDataToClusters(true);
            // check
            List<Centroid> centroidCandidates = new ArrayList<>();
            centroidCandidates.addAll(this.centroids);
            long iterations = Math.round(Math.log(this.error));
            for (int iteration = 0; iteration < iterations; iteration++) {
                System.out.println("Finding centroid approximation: "+(iteration+1) + " / "+iterations);
                // step 2: compute distances
                List<Centroid> newCentroids = Collections.synchronizedList(new ArrayList<>());
                List<Integer> indicesToRemove = Collections.synchronizedList(new ArrayList<>());
                IntStream.range(0, dataPointsRemaining.size()).parallel().forEach(i -> {
                    DataPoint dp = dataPointsRemaining.get(i);
                    double prob = (dp.squareDist * l) / this.error;
                    double rand = random.nextDouble();
                    if (prob > rand) {
                        // add sample
                        INDArray centroid = dp.dataPoint.dup();
                        newCentroids.add(new Centroid(centroid));
                        indicesToRemove.add(i);
                    }
                });
                this.centroids.clear();
                if(newCentroids.isEmpty()) {
                    System.out.println("Warning: No centroids found during iteration "+(iteration+1));
                    break;
                }
                this.centroids.addAll(newCentroids);
                centroidCandidates.addAll(newCentroids);
                this.centroidMatrix = Nd4j.vstack(newCentroids.stream().map(centroid->centroid.mean).collect(Collectors.toList()));
                indicesToRemove.stream().sorted(Comparator.reverseOrder()).forEach(idx->dataPointsRemaining.remove(idx.intValue()));
                System.out.println("Reassigning data...");
                this.error = this.reassignDataToClusters(false);
            }
            // compute w
            Map<String,Double> w = Collections.synchronizedMap(new HashMap<>());
            Map<String,INDArray> centroidMap = Collections.synchronizedMap(new HashMap<>());
            for (int i = 0; i < centroidCandidates.size(); i++) {
                Centroid centroid = centroidCandidates.get(i);
                w.put(String.valueOf(i),((double)centroid.dataPoints.size())/dataPoints.size());
                centroidMap.put(String.valueOf(i),centroid.mean);
            }
            System.out.println("Recentering "+centroidMap.size()+" centroids...");
            KMeans child = new KMeans(k,distanceFunction,false);
            child.fit(centroidMap,w,maxNumEpochsForCentroidInit,true);
            List<Centroid> newCentroids = child.getCentroids();
            this.centroids.clear();
            centroidCandidates.clear();
            int vectorSize = dataPoints.get(0).dataPoint.length();
            this.centroidMatrix=Nd4j.create(k,vectorSize);
            AtomicInteger idx = new AtomicInteger(0);
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
                this.error = reassignDataToClusters(true);

                // step 2: compute distances
                Map<String,Double> probabilities = dataPointsRemaining.parallelStream().collect(Collectors.toMap(d->d.name,d->{
                    double prob = d.squareDist/this.error;
                    if(sampleProbabilities!=null) {
                        prob = (prob+sampleProbabilities.get(d.name))/2d;
                    }
                    return prob;
                }));

                DataPoint dataPointSample = pickRandomly(dataPointsRemaining,probabilities,random);
                if(dataPointSample!=null) {
                    INDArray centroid = dataPointSample.dataPoint.dup();
                    this.centroids.add(new Centroid(centroid));
                    this.centroidMatrix = Nd4j.vstack(this.centroidMatrix, centroid);
                }
            }
        }
        return this.centroids;
    }

    private void recomputeClusterAverages() {
        centroids.parallelStream().forEach(Centroid::recomputeMean);
        // rebuild global centroid matrix
        this.centroidMatrix = Nd4j.vstack(centroids.stream().map(centroid->centroid.mean).collect(Collectors.toList()));
    }

    // TODO Most Computationally intensive part (speed this up!)
    private double reassignDataToClusters(boolean forceAssign) {
        AtomicInteger cnt = new AtomicInteger(0);
        return dataPoints.stream().mapToDouble(dataPoint->{
            Centroid centroid = findClosestCentroid(dataPoint,forceAssign);
            if(centroid!=null) {
                if (dataPoint.centroid != null && !dataPoint.centroid.equals(centroid)) {
                    dataPoint.centroid.dataPoints.remove(dataPoint);
                }
                centroid.dataPoints.add(dataPoint);
                dataPoint.centroid = centroid;
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.print("-");
            }
             return dataPoint.squareDist;
        }).sum();
    }

    private Centroid findClosestCentroid(DataPoint dataPoint, boolean forceAssign) {
        INDArray distanceMatrix = distanceFunction.apply(dataPoint.dataPoint,centroidMatrix);
        int bestChoice = Nd4j.argMax(distanceMatrix.neg(),0).getInt(0);
        double squareDist = Math.pow(distanceMatrix.getDouble(bestChoice),2);
        if(forceAssign||dataPoint.squareDist>squareDist) {
            dataPoint.squareDist = squareDist;
        } else {
            return null;
        }
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
            this.squareDist = Double.MAX_VALUE;
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
            int k = 10;
            int l = 2*k;
            KMeans kMeans = new KMeans(k, l, DistanceFunctions.PRENORM_COSINE_DISTANCE_FUNCTION);

        Map<String,INDArray> dataMap = new HashMap<>();
        for(int i = 0; i < 10000; i++) {
            dataMap.put("v"+i, Transforms.unitVec(Nd4j.randn(new int[]{1,32})));
        }

        kMeans.fit(dataMap, 20, false);

        System.out.println(kMeans.toString());
    }
}
