package models.kmeans;

import lombok.Getter;
import org.deeplearning4j.berkeley.Triple;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.indexaccum.IMax;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Evan on 11/27/2017.
 */
public class UnitCosineKMeans {
    private static final double EPSILON = 0.0000001;
    @Getter
    private List<Centroid> centroids;
    private List<DataPoint> dataPoints;
    private double error;
    private INDArray centroidMatrix;
    private INDArray V;
    private INDArray Ctranspose;
    @Getter
    private boolean converged;
    private List<DataPoint>  dataPointsClusterCandidates;
    private INDArray minBoundary;
    private INDArray maxBoundary;
    private INDArray boundaryDiff;
    private Random randomSampler = new Random(235);
    public UnitCosineKMeans() {
        this.converged=false;
    }

    protected void computeBoundingBox() {
        if(minBoundary==null) {
            minBoundary = V.min(0);
        }
        if(maxBoundary==null) {
            maxBoundary = V.max(0);
        }
        if(boundaryDiff==null) {
            boundaryDiff = maxBoundary.sub(minBoundary);
        }
    }

    protected INDArray sampleFromNullDistribution(int numSamples) {
        if(minBoundary==null||boundaryDiff==null) computeBoundingBox();
        INDArray samples = Nd4j.rand(numSamples,V.columns(),randomSampler.nextInt());
        samples.muliRowVector(boundaryDiff).addiRowVector(minBoundary);
        samples.diviColumnVector(samples.norm2(1));
        return samples;
    }




    public List<Set<String>> getClusters() {
        List<Set<String>> clusters = new ArrayList<>();
        centroids.stream().sorted(Comparator.comparing(c->c.getOrComputeFinalError())).forEach(centroid->{
            Set<String> points = new HashSet<>();
            centroid.dataPoints.forEach(point->{
                points.add(point.name);
            });
            clusters.add(points);
        });
        return clusters;
    }

    private void setVAndDatapoints(Map<String,INDArray> dataMap) {
        this.V = Nd4j.create(dataMap.size(),dataMap.values().stream().findAny().get().length());
        AtomicInteger cnt = new AtomicInteger(0);
        this.dataPoints = dataMap.entrySet().stream().map(e->{
            int idx = cnt.getAndIncrement();
            V.putRow(idx,e.getValue());
            return new DataPoint(e.getKey(),idx);
        }).collect(Collectors.toList());

    }

    public int optimize(Map<String,INDArray> dataMap, int minK, int maxK, int nSamplesPerInterval, int B, int nEpochs) {
        if(dataMap.isEmpty()) return 0;

        setVAndDatapoints(dataMap);

        return optimize(minK,maxK,nSamplesPerInterval,B,nEpochs);
    }

    protected int optimize(int minK, int maxK, int nSamplesPerInterval, int B, int nEpochs) {
        if(this.V==null||this.dataPoints==null) throw new NullPointerException("Please initialize V and data points list.");

        UnitCosineKMeans nullDistribution = new UnitCosineKMeans();
        nullDistribution.dataPoints = IntStream.range(0,this.dataPoints.size()).mapToObj(i->new DataPoint(String.valueOf(i),i)).collect(Collectors.toList());
        List<INDArray> nullDatasets = IntStream.range(0,B).mapToObj(i->sampleFromNullDistribution(this.dataPoints.size())).collect(Collectors.toList());

        int optimalK = optimizeHelper(minK,maxK,nSamplesPerInterval,nEpochs,nullDatasets,nullDistribution,new Double[nSamplesPerInterval]);
        fit(optimalK,nEpochs);

        System.out.println("Final score: "+error+". Best k: "+optimalK);
        return optimalK;
    }

    private double computeGap(int k, int nEpochs, List<INDArray> nullDatasets, UnitCosineKMeans nullDistribution) {
        fit(k, nEpochs);
        double thisError = error;
        double nullError = nullDatasets.stream().mapToDouble(ds -> {
            nullDistribution.fit(ds, k, nEpochs);
            return nullDistribution.error;
        }).average().orElse(Double.NaN);
        return Math.log(1d + nullError) - Math.log(1d + thisError);
    }

    private static int argMax(Double[] in) {
        if(in.length==0) return -1;
        int i = 0;
        double max = in[0];
        for(int j = 1; j < in.length; j++) {
            Double x = in[j];
            if(x==null)continue;
            if(x>max) {
                max = x;
                i = j;
            }
        }
        return i;
    }

    private static int lastArgMax(Double[] in) {
        if(in.length==0) return -1;
        int i = 0;
        double max = in[0];
        for(int j = 1; j < in.length; j++) {
            Double x = in[j];
            if(x==null)continue;
            if(x>=max) {
                max = x;
                i = j;
            }
        }
        return i;
    }

    private static int argMax(double[] in) {
        if(in.length==0) return -1;
        int i = 0;
        double max = in[0];
        for(int j = 1; j < in.length; j++) {
            double x = in[j];
            if(x>max) {
                max = x;
                i = j;
            }
        }
        return i;
    }

    private static int argMin(double[] in) {
        if(in.length==0) return -1;
        int i = 0;
        double min = in[0];
        for(int j = 1; j < in.length; j++) {
            double x = in[j];
            if(x<min) {
                min=x;
                i = j;
            }
        }
        return i;
    }

    protected Integer optimizeHelper(int minK, int maxK, int nSamplesPerInterval, int nEpochs, List<INDArray> nullDatasets, UnitCosineKMeans nullDistribution, Double... scores) {
        int intervalLength = Math.max(1,(maxK-minK)/nSamplesPerInterval);

        // fit min
        int i = 0;
        double[] diffs = new double[scores.length-1];
        Arrays.fill(diffs,Double.MAX_VALUE);
        boolean hasNegative = false;
        for(; i < nSamplesPerInterval; i++) {
            int j = minK+i*intervalLength;
            if(j>=maxK) {
                scores[i]=null;
            } else {
                scores[i] = computeGap(j, nEpochs, nullDatasets, nullDistribution);
                if(i>0) {
                    diffs[i-1]=(scores[i]-scores[i-1]);
                    if(diffs[i-1]<0) hasNegative=true;
                }
            }

        }

        System.out.println("k: ["+minK+","+maxK+"], Diffs: "+Arrays.toString(diffs));
        if(intervalLength<=1) {
            int finalK = minK+argMax(scores);
            System.out.println("Final k: " + finalK);
            return finalK;
        }

        int minDiffIdx = hasNegative ? argMin(diffs) : (lastArgMax(scores));

        int newMinK;
        int newMaxK;

        newMinK = Math.max(minK,Math.min(minK+(minDiffIdx)*intervalLength,maxK-intervalLength));
        newMaxK = Math.min(maxK,minK+(minDiffIdx+1)*intervalLength);
        System.out.println("Best idx: "+minDiffIdx);

        return optimizeHelper(newMinK,newMaxK,nSamplesPerInterval, nEpochs,nullDatasets,nullDistribution,scores);
    }

    public void fit(int k, int nEpochs) {
        this.centroids = initializeCentroids(k);

        Double lastError = null;
        for(int n = 0; n < nEpochs; n++) {
            //System.out.println("Starting epoch: "+(n+1));

            // reassign points to nearest cluster
            this.error = this.reassignDataToClusters();
            //System.out.println("Current error: "+error);

            // recenter centroids
            this.recomputeClusterAverages();

            // check convergence
            if(lastError!=null&&Math.abs(error-lastError)<EPSILON) {
                this.converged=true;
                break;
            }

            lastError = this.error;
        }
        //System.out.println("Converged: "+isConverged());
    }


    public void fit(INDArray V, int k, int nEpochs) {
        if(dataPoints==null||V.rows()!=dataPoints.size()) throw new RuntimeException("V must have rows() == dataPoints.size()");
        this.V=V;
        fit(k,nEpochs);
    }

    public void fit(Map<String,INDArray> dataMap, int k, int nEpochs) {
        if(dataMap.isEmpty()) return;

        setVAndDatapoints(dataMap);

        fit(k,nEpochs);
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

    private List<Centroid> initializeCentroids(int k) {
        //System.out.println("Building centroids...");

        Random random = new Random(6342);
        if(this.centroids==null) {
            this.centroids = Collections.synchronizedList(new ArrayList<>());
            dataPointsClusterCandidates = Collections.synchronizedList(new ArrayList<>(this.dataPoints));

            // step 1: pick one center uniformly at random
            DataPoint dataPoint0 = pickRandomly(dataPointsClusterCandidates, null, random);
            INDArray centroid0 = V.getRow(dataPoint0.index).dup();
            this.centroids.add(new Centroid(centroid0));
            this.centroidMatrix = centroid0;
        }


         /* k-means++ algorithm
            1- Choose one center uniformly at random from among the data points.
            2- For each data point x, compute D(x), the distance between x and the nearest center that has already been chosen.
            3- Choose one new data point at random as a new center, using a weighted probability distribution where a point x is chosen with probability proportional to D(x)2.
            4- Repeat Steps 2 and 3 until k centers have been chosen.
         */
        // recluster the weighted points in C into k clusters
        while(this.centroids.size()<k&&!dataPointsClusterCandidates.isEmpty()) {
            this.error = reassignDataToClusters();

            // step 2: compute distances
            Map<String,Double> probabilities = dataPointsClusterCandidates.parallelStream().collect(Collectors.toMap(d->d.name,d->d.squareDist/this.error));

            DataPoint dataPointSample = pickRandomly(dataPointsClusterCandidates,probabilities,random);
            if(dataPointSample!=null) {
                INDArray centroid = V.getRow(dataPointSample.index).dup();
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
        private Centroid centroid;
        private double squareDist;
        private int index;
        DataPoint(String name, int idx) {
            this.name=name;
            this.index=idx;
            this.squareDist = Double.MAX_VALUE;
        }

        @Override
        public String toString() {
            return "DataPoint["+name+"="+index+"]";
        }
    }

    private class Centroid {
        private INDArray mean;
        private Set<DataPoint> dataPoints;
        private Double finalError;
        Centroid(INDArray mean) {
            this.mean=mean;
            this.dataPoints = Collections.synchronizedSet(new HashSet<>());
        }

        private double getOrComputeFinalError() {
            if(finalError==null) {
                finalError = dataPoints.stream().mapToDouble(dp -> dp.squareDist).average().orElse(Double.MAX_VALUE);
            }
            return finalError;
        }

        private void recomputeMean() {
            if(!dataPoints.isEmpty()) {
                mean = Transforms.unitVec(V.getRows(dataPoints.stream().mapToInt(dp -> dp.index).toArray()).mean(0));
            }
        }

        @Override
        public String toString() {
            return "Centroid[mean="+mean.toString()+", datapoints=("+String.join(",",dataPoints.stream().map(dp->dp.toString()).collect(Collectors.toList()))+")]";
        }
    }


    public static void main(String[] args) {
        // test
        Random random = new Random(3);

        int numTests = 1;
        double error = 0d;
        for(int n = 0; n < numTests; n++) {
            long t0 = System.currentTimeMillis();
            int maxK = 66;
            int B = 10;
            int maxClusters = random.nextInt(maxK);
            int numPerCluster = 10;
            int nSamplesPerInterval = maxClusters;
            UnitCosineKMeans kMeans = new UnitCosineKMeans();

            Map<String, INDArray> dataMap = new HashMap<>();


            for (int j = 0; j < maxClusters; j++) {
                double[] rand = new double[]{random.nextInt(1000) - 500, random.nextInt(1000) - 500};
                for (int i = 0; i < numPerCluster; i++) {
                    dataMap.put("a" + j + "-" + i, Transforms.unitVec(Nd4j.create(new double[][]{rand}).addi(Nd4j.randn(new int[]{1, 2}).muli(0.01))));
                }
            }

            System.out.println("Trying to cluster with k="+maxClusters);
            int optimalK = kMeans.optimize(dataMap, 2, maxK, nSamplesPerInterval, B, 100);

            error += Math.abs(maxClusters-optimalK);

            long t1 = System.currentTimeMillis();
            //System.out.println(kMeans.toString());
            System.out.println("Completed in " + (t1 - t0) / 1000 + " seconds");
        }

        System.out.println("Average error: "+(error/numTests));
    }
}
