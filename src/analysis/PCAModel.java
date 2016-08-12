package analysis;

import Jama.Matrix;
import com.mkobos.pca_transform.PCA;
import seeding.Constants;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/12/16.
 */
public class PCAModel {
    private static Matrix getData(boolean training) throws Exception {
        Random rand = new Random(41);
        SimilarPatentFinder finder = new SimilarPatentFinder(null, new File("candidateSets/2"));
        double[][] data = new double[finder.getPatentList().size()][Constants.VECTOR_LENGTH];
        AtomicInteger incr = new AtomicInteger(0);
        if(rand.nextGaussian() > 1.5 && !training)
        finder.getPatentList().forEach(patent -> {double r = rand.nextGaussian(); if((r > 1.5 && !training) || (r <= 1.5 && training)) {
            data[incr.getAndIncrement()]=patent.getVector().data().asDouble();
        }});
        return new Matrix(data);
    }


    /** An example program using the library */
    public static void main(String[] args) throws Exception {
        System.out.println("Running a demonstration program on some sample data ...");
        /** Training data matrix with each row corresponding to data point and
         * each column corresponding to dimension. */
        Matrix trainingData = getData(true);
        PCA pca = new PCA(trainingData);
        /** Test data to be transformed. The same convention of representing
         * data points as in the training data matrix is used. */
        Matrix testData = getData(false);
        /** The transformed test data. */
        Matrix transformedData =
                pca.transform(testData, PCA.TransformationType.WHITENING);
        System.out.println("Transformed data (each row corresponding to transformed data point):");
        for(int r = 0; r < transformedData.getRowDimension(); r++){
            for(int c = 0; c < transformedData.getColumnDimension(); c++){
                System.out.print(transformedData.get(r, c));
                if (c == transformedData.getColumnDimension()-1) continue;
                System.out.print(", ");
            }
            System.out.println("");
        }
    }
}
