package learning;

import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/21/16.
 */
public class SimilarityAutoEncoderIterator implements DataSetIterator {
    private int batchSize;
    private int numVectors;
    private int num1DVectors;
    private int num2DVectors;
    private ResultSet results;
    private String query;

    public SimilarityAutoEncoderIterator(int batchSize, List<String> oneDNames, List<String> twoDNames) {
        this.batchSize=batchSize;
        this.num1DVectors = oneDNames.size();
        this.num2DVectors = twoDNames.size();
        this.numVectors=(num2DVectors*Constants.NUM_ROWS_OF_WORD_VECTORS)+num1DVectors;
        buildQuery(oneDNames,twoDNames);
    }

    private void buildQuery(List<String> oneDNames, List<String> twoDNames) {
        StringJoiner select = new StringJoiner(",","SELECT "," FROM patent_vectors ");
        for(String s : oneDNames) {
            select.add(s);
        }
        for(String s : twoDNames) {
            select.add(s);
        }
        StringJoiner where = new StringJoiner(" AND "," WHERE ", "");
        for(String s : oneDNames) {
            where.add(s+" IS NOT NULL");
        }
        for(String s : twoDNames) {
            where.add(s+" IS NOT NULL");
        }
        query = select.toString()+where.toString();
        System.out.println(query);
    }

    private static Double[] flatten2Dto1D(Double[][] array2D) {
        if(array2D==null || array2D.length==0)return null;
        int innerLength = array2D[0].length;
        Double[] array1D = new Double[array2D.length*innerLength];
        for(int i = 0; i < array2D.length; i++) {
            for(int j = 0; j < innerLength; j++) {
                array1D[i*innerLength+j]=array2D[i][j];
            }
        }
        return array1D;
    }

    @Override
    public DataSet next(int num) {
        try {
            return nextDataSet(num);
        } catch(SQLException sql) {
            throw new RuntimeException("SQL ERROR");
        }
    }

    private DataSet nextDataSet(int num) throws SQLException {
        if(results==null) reset();
        INDArray features = Nd4j.create(num, inputColumns());
        AtomicInteger colCount = new AtomicInteger(0);
        AtomicInteger rowCount = new AtomicInteger(0);
        int currentRowCount;
        while((currentRowCount = rowCount.getAndIncrement())<num) {
            results.next();
            for (int i = 1; i <= num1DVectors; i++) {
                for (double d : (Double[]) results.getArray(i).getArray()) {
                    features.put(currentRowCount, colCount.getAndIncrement(), d);
                }
            }
            for (int i = num1DVectors+1; i <=num1DVectors+num2DVectors; i++) {
                Double[][] array2D = (Double[][])results.getArray(i).getArray();
                Double[] array1D = flatten2Dto1D(array2D);
                for (double d : array1D) {
                    features.put(currentRowCount, colCount.getAndIncrement(), d);
                }
            }
            colCount.set(0);
        }
        return new DataSet(features,features);
    }

    @Override
    public int totalExamples() {
        return 0;
    }

    @Override
    public int inputColumns() {
        return Constants.VECTOR_LENGTH*numVectors;
    }

    @Override
    public int totalOutcomes() {
        return inputColumns();
    }

    @Override
    public void reset() {
        try {
            resetQuery();
        } catch (SQLException sql ) {
            sql.printStackTrace();
        }
    }

    private void resetQuery() throws SQLException {
        results = Database.executeQuery(query);
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        return 0;
    }

    @Override
    public int numExamples() {
        return 0;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor preProcessor) {

    }

    @Override
    public List<String> getLabels() {
        return null;
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
}
