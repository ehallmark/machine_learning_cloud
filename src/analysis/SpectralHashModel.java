package analysis;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/15/16.
 *
 *      protected double[][] data;
        protected String[] personNames;
        protected String[] labels;
        protected INDArray eigenVectors;
        protected INDArray transformed;
        protected SimilarPatentFinder finder;

 * Finding the principal components of the data using PCA.
 ** Calculating the k smallest single-dimension analytical eigenfunctions of Lp using a rectangular approximation along every PCA direction.
 This is done by evaluating the k smallest eigenvalues for each direction using (equation 4),
 thus creating a list of dk eigenvalues, and then sorting this list to find the k smallest eigenvalues.
 *** Thresholding the analytical eigenfunctions at zero, to obtain binary codes.
 */

public class SpectralHashModel extends PCAModel {
    private int k;
    public SpectralHashModel(int k) throws Exception {
        super(false);
        this.k = k;
        // 1
        PriorityQueue<EigenFunction> functions = new PriorityQueue<>(k * eigenVectors.rows(), new Comparator<EigenFunction>() {
            @Override
            public int compare(EigenFunction o1, EigenFunction o2) {
                return Double.compare(o1.getVal(),o2.getVal());
            }
        });
        for(int i = 0; i < eigenVectors.rows(); i++) {
            functions.add(new EigenFunction(eigenValues[i]));
        }
        // take the top k
        AtomicInteger cnt = new AtomicInteger(0);
        functions.removeIf(f->cnt.getAndIncrement()>=k);



    }


    @Override
    public INDArray transform(double[][] data) {
        return super.transform(data);
    }

    @Override
    public INDArray transform(INDArray matrix) {
        INDArray transform = super.transform(matrix);
        INDArray codes = Nd4j.create(matrix.rows(), k);
        for(int row = 0; row < matrix.rows(); row++) {
            for(int i = 0; i < k; i++) {
                codes.putScalar(row, i, new EigenFunction(transform.getDouble(row,i)).getStochasticVal());
            }
        }
        return codes;
    }

    @Override
    public SimilarPatentFinder getFinder() {
        return super.getFinder();
    }

    @Override
    public INDArray getEigenVectors() {
        return super.getEigenVectors();
    }

    class EigenFunction {
        private double x;
        private int k;
        private Double val;
        EigenFunction(double x) {
            this.x=x;
        }
        public double getVal() {
            if(val==null)calculate();
            return val;
        }

        public double getStochasticVal() {
            if(val==null)calculate();
            return Math.signum(val);
        }

        private void calculate() {
            val= Math.sin(Math.PI*(x*k + 1.0)/2.0);
        }
    }


    public static void main(String[] args) throws Exception {
        System.out.println("Running a demonstration program on some sample data ...");
        /** Training data matrix with each row corresponding to data point and
         * each column corresponding to dimension. */
        SpectralHashModel model = new SpectralHashModel(10);
        // Write eigenvectors to file to save
        ObjectOutputStream o = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Constants.PCA_MATRIX_FILE))));
        o.writeObject(model.getEigenVectors());
        o.flush();
        o.close();
        // Write patents to list file
        SimilarPatentFinder finder = model.getFinder();
        List<Patent> toWriteToFile = new ArrayList<>(finder.getPatentList().size());
        AtomicInteger cnt = new AtomicInteger(0);
        finder.getPatentList().forEach(p->{
            Patent encodedPatent = new PCAPatent(p.getName(), model.transform(p.getVector()), Patent.Type.ALL);
            System.out.println(encodedPatent.getName()+": "+p.getVector().toString()+" => "+encodedPatent.getVector().toString());
            toWriteToFile.add(encodedPatent);
        });
        ObjectOutputStream ois = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(Constants.PCA_PATENTS_LIST_FILE))));
        ois.writeObject(toWriteToFile);
        ois.flush();
        ois.close();
    }
}
