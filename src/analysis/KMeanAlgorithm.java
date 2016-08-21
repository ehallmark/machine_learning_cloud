package analysis;

/**
 * Created by ehallmark on 8/19/16.
 */

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class KMeanAlgorithm
{
    private static final int NUM_CLUSTERS = 2;    // Total clusters.

    private static ArrayList<Data> dataSet = new ArrayList<>();
    private static ArrayList<Centroid> centroids = new ArrayList<>();

    public KMeanAlgorithm() {
        super();
    }

    private static void initialize(List<INDArray> list)
    {
        System.out.println("Centroids initialized at:");
        assert list.size() > 1 : "Not enough data to compare!";
        Collections.sort(list,(INDArray o1, INDArray o2)->Double.compare(o1.sumNumber().doubleValue(),o2.sumNumber().doubleValue()));
        for(int i = 0; i < NUM_CLUSTERS; i++) {
            centroids.add(new Centroid(list.get(((list.size()-1)/NUM_CLUSTERS)*i).data().asDouble()));
        }

        for(Centroid c : centroids) {
            System.out.println("     " + Arrays.toString(c.coords()));
        }
        System.out.print("\n");
        return;
    }

    private static void kMeanCluster(Iterator<INDArray> iter)
    {
        System.out.println("Starting clusting");
        final double bigNumber = Math.pow(10, 10);    // some big number that's sure to be larger than our data range.
        double minimum;                   // The minimum value to beat.
        double distance;                        // The current minimum value.
        int cluster = 0;
        boolean isStillMoving = true;
        Data newData;

        // Add in new data, one at a time, recalculating centroids with each new one.
        while(iter.hasNext())
        {
            newData = new Data(iter.next().data().asDouble());
            dataSet.add(newData);
            minimum = bigNumber;
            for(int i = 0; i < NUM_CLUSTERS; i++)
            {
                distance = dist(newData, centroids.get(i));
                if(distance < minimum){
                    minimum = distance;
                    cluster = i;
                }
            }
            newData.cluster(cluster);

            // calculate new centroids.
            for(int i = 0; i < NUM_CLUSTERS; i++)
            {
                double[] totals = new double[Constants.VECTOR_LENGTH];
                int totalInCluster = 0;
                for(int j = 0; j < dataSet.size(); j++)
                {
                    if(dataSet.get(j).cluster() == i){
                        AtomicInteger cnt = new AtomicInteger(0);
                        for(double coord : dataSet.get(j).coords()) {
                            totals[cnt.getAndIncrement()]+=coord;
                        }
                        totalInCluster++;
                    }
                }
                if(totalInCluster > 0){
                    divideInPlace(centroids.get(i).coords(),totalInCluster);
                }
            }
        }

        // Now, keep shifting centroids until equilibrium occurs.
        while(isStillMoving)
        {
            // calculate new centroids.
            for(int i = 0; i < NUM_CLUSTERS; i++)
            {
                double[] totals = new double[Constants.VECTOR_LENGTH];
                int totalInCluster = 0;
                for(int j = 0; j < dataSet.size(); j++)
                {
                    if(dataSet.get(j).cluster() == i){
                        AtomicInteger cnt = new AtomicInteger(0);
                        for(double coord : dataSet.get(j).coords()) {
                            totals[cnt.getAndIncrement()]+=coord;
                        }
                        totalInCluster++;
                    }
                }
                if(totalInCluster > 0){
                    divideInPlace(centroids.get(i).coords(),totalInCluster);
                }
            }

            // Assign all data to the new centroids
            isStillMoving = false;

            for(int i = 0; i < dataSet.size(); i++)
            {
                Data tempData = dataSet.get(i);
                minimum = bigNumber;
                for(int j = 0; j < NUM_CLUSTERS; j++)
                {
                    distance = dist(tempData, centroids.get(j));
                    if(distance < minimum){
                        minimum = distance;
                        cluster = j;
                    }
                }
                tempData.cluster(cluster);
                if(tempData.cluster() != cluster){
                    tempData.cluster(cluster);
                    isStillMoving = true;
                }
            }
        }
        return;
    }

    private static void divideInPlace(double[] totals, double by) {
        for(int i = 0; i < totals.length; i++) {
            totals[i]/=by;
        }
    }

    /**
     * // Calculate Euclidean distance.
     * @param d - Data object.
     * @param c - Centroid object.
     * @return - double value.
     */
    private static double dist(Data d, Centroid c)
    {
        return Transforms.cosineSim(Nd4j.create(d.coords()),Nd4j.create(c.coords()));
    }

    private static class Data
    {
        private double[] coords;
        private int mCluster = 0;

        public Data()
        {
            return;
        }

        public Data(double... coords)
        {
            this.coords=coords;
            return;
        }

        public void coords(double... coords) {
            this.coords=coords;
        }

        public double[] coords() {
            return this.coords;
        }

        public void cluster(int clusterNumber)
        {
            this.mCluster = clusterNumber;
            return;
        }

        public int cluster()
        {
            return this.mCluster;
        }
    }

    private static class Centroid
    {
        private double[] coords;

        public Centroid()
        {
            return;
        }

        public Centroid(double... coords)
        {
            this.coords=coords;
            return;
        }

        public void coords(double... coords) {
            this.coords=coords;
        }
        public double[] coords()
        {
            return this.coords;
        }

    }

    public static void main(String[] args) throws Exception
    {

        SimilarPatentFinder finder = new SimilarPatentFinder(null, new File("candidateSets/2"));
        initialize(finder.getPatentList().stream().map(p->p.getVector()).collect(Collectors.toList()));
        kMeanCluster(finder.getPatentList().stream().map(f->f.getVector()).iterator());


        // Print out centroid results.
        System.out.println("Centroids finalized at:");
        for(int i = 0; i < NUM_CLUSTERS; i++)
        {
            System.out.println("     " + Arrays.toString(centroids.get(i).coords()));
        }
        System.out.print("\n");
        return;
    }
}
