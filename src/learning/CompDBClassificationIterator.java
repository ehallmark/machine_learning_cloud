package learning;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.util.FeatureUtil;
import seeding.Database;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/19/16.
 */
public class CompDBClassificationIterator extends AbstractPatentIterator {
    private int numClassifications;

    public CompDBClassificationIterator(int batchSize, List<String> oneDNames, List<String> twoDNames, boolean isTraining) {
        super(batchSize, oneDNames, twoDNames, isTraining);
    }

    @Override
    protected String buildAndReturnQuery(List<String> oneDNames, List<String> twoDNames, boolean isTraining) {
        try {
            numClassifications = Database.getNumberOfCompDBClassifications();
        }catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("Unable to get number of compdb classifications!");
        }

        StringJoiner select = new StringJoiner(",", "SELECT ", " FROM patent_vectors ");
        for (String s : oneDNames) {
            select.add(s);
        }
        for (String s : twoDNames) {
            select.add(s);
        }
        // add labels array
        select.add("compdb_technologies");
        StringJoiner where = new StringJoiner(" AND ", " WHERE ", "");
        where.add("compdb_technologies IS NOT NULL");
        for (String s : oneDNames) {
            where.add(s + " IS NOT NULL");
        }
        for (String s : twoDNames) {
            where.add(s + " IS NOT NULL");
        }
        if (isTraining) where.add("is_testing = 'f'");
        else where.add("is_testing = 't'");

        String builtQuery = select.toString() + where.toString();
        System.out.println(builtQuery);
        return builtQuery;
    }

    @Override
    protected DataSet nextDataSet(int num) throws SQLException {
        if(results==null) reset();
        INDArray features = Nd4j.create(num, inputColumns());
        INDArray labels = Nd4j.zeros(num, totalOutcomes());
        AtomicInteger rowCount = new AtomicInteger(0);
        int currentRowCount;
        Set<Integer> set = new HashSet<>();
        while(!(results.isLast()||results.isAfterLast())&&(currentRowCount = rowCount.getAndIncrement())<num) {
            results.next();
            AtomicInteger colCount = new AtomicInteger(0);
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
            // get labels
            int labelIndex = num1DVectors+num2DVectors+1;
            colCount.set(0);
            for (double d : (Double[]) results.getArray(labelIndex).getArray()) {
                int index = colCount.getAndIncrement();
                if(d > 0.0) {
                    labels.put(currentRowCount, index, 1);
                    set.add(index);
                }
            }
        }
        System.out.println("Number of distinct labels: "+set.size());
        return new DataSet(features,labels);
    }


    @Override
    public int totalOutcomes() {
        return numClassifications;
    }
}

