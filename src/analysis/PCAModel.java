package analysis;

import Jama.Matrix;
//import com.mkobos.pca_transform.PCA;
import flanagan.analysis.PCA;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/12/16.
 */
public class PCAModel {
    protected double[][] data;
    protected String[] personNames;
    protected String[] labels;
    protected INDArray eigenVectors;
    protected PCA pca;
    protected SimilarPatentFinder finder;


    public PCAModel(boolean truncate) throws  Exception {
        setDataAndItemNames();
        pca = new PCA();
        pca.enterItemNames(labels);
        pca.enterPersonNames(personNames);
        pca.enterScoresAsRowPerPerson(data);

        int numRelevantEigenVectors = pca.nEigenOneOrGreater();
        System.out.println("NUMBER OF RELEVANT DIMENSIONS (EIGENVALUES): "+numRelevantEigenVectors);
        double[] eigenValues = pca.orderedEigenValues();
        assert eigenValues[0] > eigenValues[1] : "Eigenvalues are sorted incorrectly!!";

        eigenVectors = truncate ? Nd4j.create(Arrays.copyOfRange(pca.orderedEigenVectorsAsRows(), 0, numRelevantEigenVectors)).transpose() : Nd4j.create(pca.orderedEigenVectorsAsColumns());
        INDArray dataVectors = Nd4j.create(data);
        System.out.println("EigenVector Matrix: "+eigenVectors.shapeInfoToString());
        System.out.println("Data Matrix: "+dataVectors.shapeInfoToString());
    }


    public INDArray transform(double[][] data) {
        return transform(Nd4j.create(data));
    }

    public INDArray transform(INDArray inVec) {
        assert inVec.columns()==eigenVectors.rows() : "INVALID DIMENSIONS!";
        return inVec.mmul(eigenVectors);
    }

    private void setDataAndItemNames() throws Exception {
        finder = new SimilarPatentFinder(null, new File("candidateSets/5"));
        int size = finder.getPatentList().size();
        data = new double[size][Constants.VECTOR_LENGTH];
        personNames = new String[size];
        AtomicInteger incr = new AtomicInteger(0);
        finder.getPatentList().forEach(patent -> {
            int idx = incr.getAndIncrement();
            data[idx]=patent.getVector().data().asDouble();
            personNames[idx] = patent.getName();
            System.gc();
        });
        labels = new String[Constants.VECTOR_LENGTH];
        for(int i = 0; i < labels.length; i++) {
            labels[i] = "Dim "+i;
        }
    }

    public SimilarPatentFinder getFinder() {
        return finder;
    }

    public INDArray getEigenVectors() {
        return eigenVectors;
    }

    public static INDArray loadAndReturnEigenVectors() throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(new File(Constants.PCA_MATRIX_FILE))));
        Object vec = ois.readObject();
        ois.close();
        return (INDArray) vec;
    }

    /** An example program using the library */
    public static void main(String[] args) throws Exception {
        System.out.println("Running a demonstration program on some sample data ...");
        /** Training data matrix with each row corresponding to data point and
         * each column corresponding to dimension. */
        PCAModel model = new PCAModel(true);
        // Write eigenvectors to file to save
        ObjectOutputStream o = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Constants.PCA_MATRIX_FILE))));
        o.writeObject(model.getEigenVectors());
        o.flush();
        o.close();
        // Write patents to list file
        SimilarPatentFinder finder = model.getFinder();
        List<Patent> toWriteToFile = new ArrayList<>(finder.getPatentList().size());
        AtomicInteger cnt = new AtomicInteger(0);
        finder.getPatentList().forEach(p->toWriteToFile.add(new PCAPatent(p.getName(), model.transform(p.getVector()))));
        ObjectOutputStream ois = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Constants.PCA_PATENTS_LIST_FILE))));
        ois.writeObject(toWriteToFile);
        ois.flush();
        ois.close();
    }
}
