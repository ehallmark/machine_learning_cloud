package analysis;

import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.VectorHelper;

import java.sql.ResultSet;
import java.util.List;

/**
 * Created by ehallmark on 8/30/16.
 */
public class BOWIterator implements DataSetIterator {
    private int batchSize;
    private ResultSet rs;
    private int inputColumns;
    public BOWIterator(int batchSize) throws Exception {
        this.batchSize = batchSize;
        this.inputColumns=Database.lengthOfBOW();
        reset();
    }

    @Override
    public DataSet next(int num) {
        try {
            int i = 0;
            INDArray data = Nd4j.create(num,inputColumns());
            while(i < num && rs.next()) {
                data.putRow(i, Nd4j.create(VectorHelper.toPrim((Integer[])rs.getArray(2).getArray())));
                i++;
            }
            data.divi(data.sumNumber());
            return new DataSet(data.add(Nd4j.randn(1,inputColumns).div(100.0)),data);// add some noise
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error on next!");
        }

    }

    @Override
    public int totalExamples() {
        throw new UnsupportedOperationException("totalExamples operation is not available!");
    }

    @Override
    public int inputColumns() {
        return inputColumns;
    }

    @Override
    public int totalOutcomes() {
        throw new UnsupportedOperationException("totalOutcomes operation is not available!");
    }

    @Override
    public void reset() {
        try {
            rs = Database.selectBOW(batch());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot reset result set!");
        }
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException("cursor operation is not available!");
    }

    @Override
    public int numExamples() {
        throw new UnsupportedOperationException("numExamples operation is not available!");
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {
        throw new UnsupportedOperationException("setPreProcessor operation is not available!");
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException("getLabels operation is not available!");
    }

    @Override
    public boolean hasNext() {
        try {
            return !(rs.isLast() || rs.isAfterLast());
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public DataSet next() {
        return next(batch());
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove operation is not available!");
    }

}
