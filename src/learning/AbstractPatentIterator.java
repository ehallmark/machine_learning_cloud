package learning;

import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.VectorHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/25/16.
 */
public abstract class AbstractPatentIterator implements DataSetIterator {
    protected int batchSize;
    protected int num1DVectors;
    protected int num2DVectors;
    protected int numVectors;
    protected ResultSet results;
    protected String query;

    public AbstractPatentIterator(int batchSize, List<String> oneDNames, List<String> twoDNames, boolean isTraining) {
        this.batchSize=batchSize;
        this.num1DVectors = oneDNames.size();
        this.num2DVectors = twoDNames.size();
        this.numVectors=num2DVectors+num1DVectors;
        query = buildAndReturnQuery(oneDNames,twoDNames, isTraining);
    }

    protected abstract String buildAndReturnQuery(List<String> oneDNames, List<String> twoDNames, boolean isTraining);

    @Override
    public int totalExamples() {
        throw new UnsupportedOperationException("totalExamples operation is not available!");
    }

    @Override
    public int inputColumns() {
        return Constants.VECTOR_LENGTH*numVectors;
    }

    @Override
    public DataSet next(int num) {
        try {
            return nextDataSet(num);
        } catch(SQLException sql) {
            throw new RuntimeException("SQL ERROR");
        }
    }


    @Override
    public void reset() {
        try {
            resetQuery();
        } catch (SQLException sql ) {
            sql.printStackTrace();
        }
    }

    protected void resetQuery() throws SQLException {
        if(results!=null&&!results.isClosed()) results.close();
        results = Database.executeQuery(query);
    }

    protected abstract DataSet nextDataSet(int num) throws SQLException;

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

    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException("getLabels operation is not available!");
    }

    @Override
    public boolean hasNext() {
        try {
            return results==null || !(results.isAfterLast() || results.isLast());
        } catch (SQLException sql) {
            sql.printStackTrace();
        }
        return false;
    }

    @Override
    public DataSet next() {
        return next(batchSize);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove operation is not available!");
    }

    protected DataSet getNextDataSet(int num, int numLabels) throws SQLException{
        if(results==null) reset();
        INDArray features = Nd4j.create(num, inputColumns());
        INDArray labels = null;
        if(numLabels>0)labels = Nd4j.create(num, numLabels);
        AtomicInteger rowCount = new AtomicInteger(0);
        int currentRowCount;
        final int labelIndex = num1DVectors+num2DVectors+1;
        while(results.next()&&(currentRowCount = rowCount.getAndIncrement())<num) {
            features.putRow(currentRowCount, VectorHelper.extractResultSetToVector(results));
            if(numLabels > 0) {
                // get labels
                labels.putRow(currentRowCount, Nd4j.create(VectorHelper.toPrim((Double[])results.getArray(labelIndex).getArray())));
            }
        }
        if(numLabels <= 0) return new DataSet(features,features);
        else return new DataSet(features, labels);
    }

}

