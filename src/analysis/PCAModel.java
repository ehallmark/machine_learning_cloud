package analysis;

import Jama.Matrix;
//import com.mkobos.pca_transform.PCA;
import flanagan.analysis.PCA;
import seeding.Constants;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/12/16.
 */
public class PCAModel {
    private static double[][] data;
    private static String[] personNames;
    private static String[] labels;

    private static void setDataAndItemNames(boolean training) throws Exception {
        SimilarPatentFinder finder;
        if(training) finder = new SimilarPatentFinder(null, new File("candidateSets/2"));
        else finder = new SimilarPatentFinder(null, new File("candidateSets/5"));
        int size = finder.getPatentList().size();
        data = new double[size][Constants.VECTOR_LENGTH];
        personNames = new String[size];
        AtomicInteger incr = new AtomicInteger(0);
        finder.getPatentList().forEach(patent -> {
            int idx = incr.getAndIncrement();
            data[idx]=patent.getVector().data().asDouble();
            personNames[idx] = patent.getName();
        });
        labels = new String[Constants.VECTOR_LENGTH];
        for(int i = 0; i < labels.length; i++) {
            labels[i] = "Dim "+i;
        }
    }


    /** An example program using the library */
    public static void main(String[] args) throws Exception {
        System.out.println("Running a demonstration program on some sample data ...");
        /** Training data matrix with each row corresponding to data point and
         * each column corresponding to dimension. */
        setDataAndItemNames(true);
        PCA pca = new PCA();
        pca.enterItemNames(labels);
        pca.enterPersonNames(personNames);
        pca.enterScoresAsRowPerPerson(data);

        pca.analysis();
        pca.pca();

        System.out.println("NUMBER OF RELEVANT DIMENSIONS (EIGENVALUES): "+pca.nEigenOneOrGreater());
    }
}
