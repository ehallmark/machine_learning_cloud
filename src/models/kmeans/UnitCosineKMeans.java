package models.kmeans;

import lombok.Getter;
import lombok.NonNull;
import org.deeplearning4j.berkeley.Triple;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.indexaccum.IMax;
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

    protected INDArray sampleFromNullDistribution(int numSamples,int B) {
        if(minBoundary==null||boundaryDiff==null) computeBoundingBox();
        INDArray samples = Nd4j.rand(randomSampler.nextInt(),numSamples,boundaryDiff.length(), B);
        for(int i = 0; i < B; i++) {
            INDArray sample=samples.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(i));
            sample.muliRowVector(boundaryDiff).addiRowVector(minBoundary);
            sample.diviColumnVector(sample.norm2(1));
        }
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

    public int optimize(Map<String,INDArray> dataMap, int minK, int maxK, int B, int nEpochs) {
        if(dataMap.isEmpty()) return 0;

        setVAndDatapoints(dataMap);

        return optimize(minK,maxK,B,nEpochs);
    }

    protected int optimize(int minK, int maxK, int B, int nEpochs) {
        if(this.V==null||this.dataPoints==null) throw new NullPointerException("Please initialize V and data points list.");

        if(minK==maxK) {
            fit(minK,nEpochs);
            return minK;
        }

        UnitCosineKMeans nullDistribution = new UnitCosineKMeans();
        nullDistribution.dataPoints = IntStream.range(0,this.dataPoints.size()).mapToObj(i->new DataPoint(String.valueOf(i),i)).collect(Collectors.toList());
        INDArray nullDatasets = sampleFromNullDistribution(this.dataPoints.size(),B);

        int optimalK = optimizeHelper(minK,maxK,nEpochs,nullDatasets,nullDistribution);
        fit(optimalK,nEpochs);

        System.out.println("Final score: "+error+". Best k: "+optimalK);
        return optimalK;
    }

    private double[] computeGap(int k, int nEpochs, INDArray nullDatasets, UnitCosineKMeans nullDistribution) {
        fit(k, nEpochs);
        double thisError = Math.log(EPSILON+Math.max(0,error));

        double[] Bscores = IntStream.range(0,nullDatasets.shape()[2]).mapToDouble(i -> {
            nullDistribution.fit(nullDatasets.get(NDArrayIndex.all(),NDArrayIndex.all(), NDArrayIndex.point(i)), k, nEpochs);
            return Math.log(EPSILON+Math.max(0,nullDistribution.error));
        }).toArray();

        double nullError = DoubleStream.of(Bscores).sum()/Bscores.length;

        double stddev = Math.sqrt(DoubleStream.of(Bscores).map(d->Math.pow(d-nullError,2)).sum()/Bscores.length);
        stddev *= Math.sqrt(1d + 1d/Bscores.length);

        double gap = nullError - thisError;

        return new double[]{gap,stddev};
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

    protected Integer optimizeHelper(int minK, int maxK, int nEpochs, INDArray nullDatasets, UnitCosineKMeans nullDistribution) {
        int intervalLength = 1;
        int width = maxK - minK;

        double gapK = Double.MIN_VALUE;
        double gapKplus1;

        // fit min
        int i = 0;
        double[] scores = new double[width/intervalLength];
        int bestIdx = -1;
        for(; i < width/intervalLength; i++) {
            int j = minK + i*intervalLength;
            if(j<maxK) {
                double[] stats = computeGap(j, nEpochs, nullDatasets, nullDistribution);
                gapKplus1 = stats[0];
                double sKplus1 = stats[1];
                scores[i]=gapKplus1;
                if(i>0) {
                    // Gap(k)>=Gap(k + 1)−sk + 1
                    double diff = (gapK - (gapKplus1-sKplus1));
                    if (diff>=0 || error < EPSILON) {
                        bestIdx = i-1;
                        break;
                    }
                }
                gapK = gapKplus1;

            } else break;
        }
        if(bestIdx<0) bestIdx = minK + intervalLength * argMax(scores);

        int finalK = Math.min(minK+ intervalLength*bestIdx,maxK);
        System.out.println("Final k: " + finalK + ", scores: "+Arrays.toString(scores));
        return finalK;
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


    public void fit(@NonNull INDArray V, int k, int nEpochs) {
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

        this.centroidMatrix = Nd4j.create(this.centroids.size(), V.columns());
        for(int i = 0; i < this.centroids.size(); i++) {
            this.centroidMatrix.putRow(i, this.centroids.get(i).mean);
        }
        return this.centroids;
    }

    private void recomputeClusterAverages() {
        centroids.parallelStream().forEach(Centroid::recomputeMean);
        // rebuild global centroid matrix
    }

    // TODO Most Computationally intensive part (speed this up!)
    private AtomicInteger cnter = new AtomicInteger(0);
    private double reassignDataToClusters() {
        this.Ctranspose = centroidMatrix.transpose();

        AtomicInteger cnt = new AtomicInteger(0);

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
        System.out.println(cnter.getAndIncrement());
        return pair.getFirst();
    }

    private static INDArray multiplyInBatches(INDArray x, INDArray y, int batchSize) {
        INDArray res = Nd4j.create(x.rows(),y.columns());
        multiplyInBatchesHelper(x,y,batchSize,0,res);
        return res;
    }

    private static void multiplyInBatchesHelper(INDArray x, INDArray y, int batchSize, int startIdx, INDArray res) {
        if(x.rows()<=batchSize) {
            res.get(NDArrayIndex.interval(startIdx,startIdx+x.rows()),NDArrayIndex.all()).assign(x.mmul(y));
        } else {
            INDArray xPart = x.get(NDArrayIndex.interval(0,batchSize),NDArrayIndex.all());
            INDArray xEnd = x.get(NDArrayIndex.interval(batchSize,x.rows()),NDArrayIndex.all());
            multiplyInBatchesHelper(xPart,y,batchSize,startIdx,res);
            multiplyInBatchesHelper(xEnd,y,batchSize,startIdx+batchSize,res);
        }
    }

    private Triple<Double,double[],int[]> findClosestCentroids(INDArray V, INDArray Ctranspose) {
        INDArray res = multiplyInBatches(V,Ctranspose,1024);
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
                mean.assign(Transforms.unitVec(V.getRows(dataPoints.stream().mapToInt(dp -> dp.index).toArray()).mean(0)));
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

        int numTests = 10;
        double error = 0d;
        for(int n = 0; n < numTests; n++) {
            long t0 = System.currentTimeMillis();
            int maxK = 20;
            int B = 10;
            int maxClusters = 2+n;
            int numPerCluster = 1000;
            UnitCosineKMeans kMeans = new UnitCosineKMeans();

            Map<String, INDArray> dataMap = new HashMap<>();

            for (int j = 0; j < maxClusters; j++) {
                INDArray rand = Nd4j.rand(1,32).subi(0.5);
                double variance = random.nextDouble()*random.nextDouble()+EPSILON;
                for (int i = 0; i < numPerCluster; i++) {
                    dataMap.put("a" + j + "-" + i, Transforms.unitVec(rand.addi(Nd4j.randn(new int[]{1, 32}).muli(variance))));
                }
            }

            System.out.println("Trying to cluster with k="+maxClusters);
            int optimalK = kMeans.optimize(dataMap, 2, maxK, B, 100);

            error += Math.abs(maxClusters-optimalK);

            long t1 = System.currentTimeMillis();
            //System.out.println(kMeans.toString());
            System.out.println("Completed in " + (t1 - t0) / 1000 + " seconds");
        }

        System.out.println("Average error: "+(error/numTests));
    }
}
