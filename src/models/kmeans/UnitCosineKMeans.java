package models.kmeans;

import data_pipeline.helpers.Function2;
import lombok.Getter;
import org.deeplearning4j.berkeley.Triple;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.indexaccum.IMax;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Evan on 11/27/2017.
 */
public class UnitCosineKMeans {
    private static final double EPSILON = 0.0000001;
    private final int k;
    @Getter
    private List<Centroid> centroids;
    private List<DataPoint> dataPoints;
    private double error;
    private double avgError;
    private double avgErrorAlpha;
    private INDArray centroidMatrix;
    private INDArray V;
    private INDArray Ctranspose;
    @Getter
    private boolean converged;
    public UnitCosineKMeans(int k) {
        this.k=k;
        this.avgError=0d;
        this.converged=false;
        this.avgErrorAlpha = 0.8;
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



    public void fit(Map<String,INDArray> dataMap, int nEpochs) {
        this.dataPoints = dataMap.entrySet().stream().map(e->new DataPoint(e.getKey(),e.getValue())).collect(Collectors.toList());
        this.V=Nd4j.vstack(this.dataPoints.stream().map(d->d.dataPoint).collect(Collectors.toList()));
        if(this.centroids==null) {
            this.centroids = initializeCentroids();
        }

        Double lastError = null;
        for(int n = 0; n < nEpochs; n++) {
            System.out.println("Starting epoch: "+(n+1));

            // reassign points to nearest cluster
            this.error = this.reassignDataToClusters();
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

    private List<Centroid> initializeCentroids() {
        System.out.println("Building centroids...");

        Random random = new Random(6342);
        this.centroids = Collections.synchronizedList(new ArrayList<>());
        List<DataPoint> dataPointsRemaining = Collections.synchronizedList(new ArrayList<>(this.dataPoints));

        // step 1: pick one center uniformly at random
        DataPoint dataPoint0 = pickRandomly(dataPointsRemaining,null,random);
        INDArray centroid0 = dataPoint0.dataPoint.dup();
        this.centroids.add(new Centroid(centroid0));
        this.centroidMatrix = centroid0;


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
            Map<String,Double> probabilities = dataPointsRemaining.parallelStream().collect(Collectors.toMap(d->d.name,d->d.squareDist/this.error));

            DataPoint dataPointSample = pickRandomly(dataPointsRemaining,probabilities,random);
            if(dataPointSample!=null) {
                INDArray centroid = dataPointSample.dataPoint.dup();
                this.centroids.add(new Centroid(centroid));
                this.centroidMatrix = Nd4j.vstack(this.centroidMatrix, centroid);
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
    private double reassignDataToClusters() {
        AtomicInteger cnt = new AtomicInteger(0);
        this.Ctranspose = centroidMatrix.transpose();

        Triple<Double,double[],int[]> pair = findClosestCentroids(V,Ctranspose);
        int[] bestCentroidIndicesPerDataPoint = pair.getThird();
        double[] errors = pair.getSecond();
        IntStream.range(0,dataPoints.size()).forEach(i->{
            int centroidIdx = bestCentroidIndicesPerDataPoint[i];
            DataPoint dataPoint = dataPoints.get(i);
            dataPoint.squareDist=errors[i];
            if(centroidIdx>=0) {
                Centroid centroid = centroids.get(centroidIdx);
                if (centroid != null) {
                    if (dataPoint.centroid != null && !dataPoint.centroid.equals(centroid)) {
                        dataPoint.centroid.dataPoints.remove(dataPoint);
                    }
                    centroid.dataPoints.add(dataPoint);
                    dataPoint.centroid = centroid;
                }
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.print("-");
            }
        });
        return pair.getFirst();
    }

    private Triple<Double,double[],int[]> findClosestCentroids(INDArray V, INDArray Ctranspose) {
        INDArray res = V.mmul(Ctranspose);
        INDArray max = res.max(1).rsubi(1d);
        double error = max.sumNumber().doubleValue();
        INDArray idxOfMaxInEachRow = Nd4j.getExecutioner().exec(new IMax(res),1);
        return new Triple<>(error,max.data().asDouble(),idxOfMaxInEachRow.data().asInt());
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
                mean = Transforms.unitVec(Nd4j.vstack(dataPoints.stream().map(dp -> dp.dataPoint).collect(Collectors.toList())).mean(0));
            }
        }

        @Override
        public String toString() {
            return "Centroid[mean="+mean.toString()+", datapoints=("+String.join(",",dataPoints.stream().map(dp->dp.toString()).collect(Collectors.toList()))+")]";
        }
    }


    public static void main(String[] args) {
        // test
        long t0 = System.currentTimeMillis();
        int k = 30;
        UnitCosineKMeans kMeans = new UnitCosineKMeans(k);

        Map<String,INDArray> dataMap = new HashMap<>();
        for(int i = 0; i < 10000; i++) {
            dataMap.put("v"+i, Transforms.unitVec(Nd4j.randn(new int[]{1,32})));
        }

        kMeans.fit(dataMap, 200);


        long t1 = System.currentTimeMillis();
        //System.out.println(kMeans.toString());
        System.out.println("Completed in "+(t1-t0)/1000+" seconds");
    }
}
