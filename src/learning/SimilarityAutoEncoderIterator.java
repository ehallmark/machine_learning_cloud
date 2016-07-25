package learning;


import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;

import java.sql.SQLException;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/21/16.
 */
public class SimilarityAutoEncoderIterator extends AbstractPatentIterator {
    public SimilarityAutoEncoderIterator(int batchSize, List<String> oneDNames, List<String> twoDNames, boolean isTraining) {
        super(batchSize, oneDNames, twoDNames, isTraining);
    }

    @Override
    protected String buildAndReturnQuery(List<String> oneDNames, List<String> twoDNames, boolean isTraining) {
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
        if(isTraining) where.add("is_testing = 'f'");
        else where.add("is_testing = 't'");

        String builtQuery = select.toString()+where.toString();
        System.out.println(builtQuery);
        return builtQuery;
    }

    @Override
    protected DataSet nextDataSet(int num) throws SQLException {
        if(results==null) reset();
        INDArray features = Nd4j.create(num, inputColumns());
        AtomicInteger rowCount = new AtomicInteger(0);
        int currentRowCount;
        while(!(results.isLast()||results.isAfterLast())&&(currentRowCount = rowCount.getAndIncrement())<num) {
            AtomicInteger colCount = new AtomicInteger(0);
            results.next();
            for (int i = 1; i <= num1DVectors; i++) {
                for (double d : (Double[]) results.getArray(i).getArray()) {
                    features.put(currentRowCount, colCount.getAndIncrement(), d);
                }
            }
            for (int i = num1DVectors+1; i <=num1DVectors+num2DVectors; i++) {
                Double[][] array2D = (Double[][])results.getArray(i).getArray();
                Double[] array1D = IteratorHelper.flatten2Dto1D(array2D);
                for (double d : array1D) {
                    features.put(currentRowCount, colCount.getAndIncrement(), d);
                }
            }
        }
        return new DataSet(features,features);
    }


    @Override
    public int totalOutcomes() {
        return inputColumns();
    }
}
