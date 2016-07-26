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
        return getNextDataSet(num, totalOutcomes());
    }


    @Override
    public int totalOutcomes() {
        return numClassifications;
    }
}

