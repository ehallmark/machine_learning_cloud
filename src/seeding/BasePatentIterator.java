package seeding;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.text.documentiterator.LabelAwareDocumentIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements LabelAwareDocumentIterator {

    protected final int startDate;
    protected ResultSet resultSet;
    protected ResultSet claimSet;
    protected Iterator<Pair<InputStream,String>> currentPatentIterator;
    protected SentencePreProcessor preProcessor;
    protected Iterator<String[]> patentNumbersGroupedByDate;
    protected List<String[]> toIter;
    protected String currentLabel;
    // used to tag each sequence with own Id

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
        preProcessor=new MyPreprocessor();
        ResultSet patentNumbers = Database.getPatentsBetween(startDate);
        toIter = new ArrayList<>();
        while(patentNumbers.next()) {
            toIter.add((String[])patentNumbers.getArray(1).getArray());
        }
    }

    protected void resetQuery() throws SQLException {
        patentNumbersGroupedByDate = toIter.iterator();
    }

    private List<Pair<InputStream,String>> processedSentenceIterator(ResultSet rs) throws SQLException {
        List<Pair<InputStream,String>> toReturn = new ArrayList<>();
        while(rs.next()) {
            toReturn.add(new Pair<>(rs.getAsciiStream(1),rs.getString(1)));
        }
        return toReturn;
    }

    public Pair<InputStream,String> nextSentence() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                return currentPatentIterator.next();
            }
            List<Pair<InputStream,String>> iter = new ArrayList<>();
            // Check for more results in result set
            String[] nums = patentNumbersGroupedByDate.next();
            claimSet=Database.getPatentVectorData(nums,true);
            iter.addAll(processedSentenceIterator(resultSet));
            resultSet=Database.getPatentVectorData(nums,false);
            iter.addAll(processedSentenceIterator(claimSet));

            currentPatentIterator = iter.iterator();
            //  System.out.println("Number of sentences for "+currentPatent+": "+preIterator.size());
            return nextSentence();

        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("SQL ERROR");
        }
    }

    @Override
    public InputStream nextDocument() {
        Pair<InputStream,String> current = nextSentence();
        System.out.println("Current Label: "+current);
        currentLabel = current.getSecond();
        return current.getFirst();
    }

    @Override
    public boolean hasNext() {
        return (currentPatentIterator==null||currentPatentIterator.hasNext()||patentNumbersGroupedByDate.hasNext());
    }

    @Override
    public void reset() {
        try {
            if(resultSet!=null && !resultSet.isClosed()) resultSet.close();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        try {
            if(claimSet!=null && !claimSet.isClosed()) claimSet.close();
        } catch(SQLException sql) {
            sql.printStackTrace();
        }
        try {
            resetQuery();
        } catch(SQLException sql) {
            sql.printStackTrace();
            throw new RuntimeException("UNABLE TO RESET QUERY");
        }
        currentPatentIterator=null;
    }

    @Override
    public String currentLabel() {
        return currentLabel;
    }
}
