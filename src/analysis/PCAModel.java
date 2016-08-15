package analysis;

import Jama.Matrix;
//import com.mkobos.pca_transform.PCA;
import flanagan.analysis.PCA;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/12/16.
 */
public class PCAModel {
    private double[][] data;
    private String[] personNames;
    private String[] labels;
    private INDArray eigenVectors;
    private INDArray transformed;
    private SimilarPatentFinder finder;

    public PCAModel() throws  Exception {
        setDataAndItemNames();
        PCA pca = new PCA();
        pca.enterItemNames(labels);
        pca.enterPersonNames(personNames);
        pca.enterScoresAsRowPerPerson(data);

        int numRelevantEigenVectors = pca.nEigenOneOrGreater();
        System.out.println("NUMBER OF RELEVANT DIMENSIONS (EIGENVALUES): "+numRelevantEigenVectors);
        double[] eigenValues = pca.orderedEigenValues();
        assert eigenValues[0] > eigenValues[1] : "Eigenvalues are sorted incorrectly!!";

        eigenVectors = Nd4j.create(Arrays.copyOfRange(pca.orderedEigenVectorsAsRows(), 0, numRelevantEigenVectors)).transpose();
        INDArray dataVectors = Nd4j.create(data);
        System.out.println("EigenVector Matrix: "+eigenVectors.shapeInfoToString());
        System.out.println("Data Matrix: "+dataVectors.shapeInfoToString());
        transformed = dataVectors.mmul(eigenVectors);
        System.out.println("Transformation: "+transformed.shapeInfoToString());
    }

    public INDArray getTransformed() {
        return transformed;
    }

    public INDArray transform(double[][] data) {
        return transform(Nd4j.create(data));
    }

    public INDArray transform(INDArray inVec) {
        assert inVec.columns()==eigenVectors.rows() : "INVALID DIMENSIONS!";
        return inVec.mmul(eigenVectors);
    }

    private void setDataAndItemNames() throws Exception {
        finder = new SimilarPatentFinder();
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


    /** An example program using the library */
    public static void main(String[] args) throws Exception {
        System.out.println("Running a demonstration program on some sample data ...");
        /** Training data matrix with each row corresponding to data point and
         * each column corresponding to dimension. */
        PCAModel model = new PCAModel();
        // Write eigenvectors to file to save
        ObjectOutputStream o = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Constants.PCA_MATRIX_FILE))));
        o.writeObject(model.getEigenVectors());
        o.flush();
        o.close();
        // Write patents to list file
        SimilarPatentFinder finder = model.getFinder();
        List<Patent> toWriteToFile = new ArrayList<>(finder.getPatentList().size());
        INDArray transformed = model.getTransformed();
        AtomicInteger cnt = new AtomicInteger(0);
        finder.getPatentList().forEach(p->toWriteToFile.add(new PCAPatent(p.getName(), transformed.getRow(cnt.getAndIncrement()), Patent.Type.ALL)));
        ObjectOutputStream ois = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Constants.PCA_PATENTS_LIST_FILE))));
        ois.writeObject(toWriteToFile);
        ois.flush();
        ois.close();
    }
}
