package learning;


import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;

import java.sql.SQLException;
import java.util.List;
import java.util.StringJoiner;

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
        return getNextDataSet(num, -1);
    }


    @Override
    public int totalOutcomes() {
        return inputColumns();
    }
}
