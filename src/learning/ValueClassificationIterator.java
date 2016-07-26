package learning;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
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
public class ValueClassificationIterator extends AbstractPatentIterator {

    public ValueClassificationIterator(int batchSize, List<String> oneDNames, List<String> twoDNames, boolean isTraining) {
        super(batchSize, oneDNames, twoDNames, isTraining);
    }

    @Override
    protected String buildAndReturnQuery(List<String> oneDNames, List<String> twoDNames, boolean isTraining) {
        StringJoiner select = new StringJoiner(",", "SELECT ", " FROM patent_vectors ");
        for (String s : oneDNames) {
            select.add(s);
        }
        for (String s : twoDNames) {
            select.add(s);
        }
        // add labels array
        select.add("CASE WHEN (is_valuable) THEN '{1}'::int[] ELSE '{0}'::int[] END");
        StringJoiner where = new StringJoiner(" AND ", " WHERE ", "");
        where.add("is_valuable IS NOT NULL");
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
        return getNextDataSet(num, 1);
    }


    @Override
    public int totalOutcomes() {
        return 1;
    }
}

