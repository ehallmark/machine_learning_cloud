package seeding;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.text.documentiterator.LabelAwareDocumentIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import tools.VectorHelper;


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
    protected String[] patentNumbersGroupedByDate;
    protected List<String[]> toIter;
    protected String currentLabel;
    protected List<Pair<InputStream,String>> iter;
    // used to tag each sequence with own Id

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
        preProcessor=new MyPreprocessor();
        ResultSet patentNumbers = Database.allPatentsArray(startDate);
        toIter = new ArrayList<>();
        if(patentNumbers.next()) {
            patentNumbersGroupedByDate=(String[])patentNumbers.getArray(1).getArray();
        }
    }


    private List<Pair<InputStream,String>> processedSentenceIterator(ResultSet rs) throws SQLException {
        List<Pair<InputStream,String>> toReturn = new ArrayList<>();
        while(rs.next()) {
            String text = rs.getString(1);
            if(VectorHelper.shouldRemoveSentence(text)) continue;
            toReturn.add(new Pair<>(new ByteArrayInputStream(text.getBytes()),rs.getString(1)));
        }
        return toReturn;
    }

    public Pair<InputStream,String> nextSentence() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                return currentPatentIterator.next();
            }
            iter = new ArrayList<>();
            // Check for more results in result set
            resultSet=Database.getPatentVectorData(patentNumbersGroupedByDate,false);
            iter.addAll(processedSentenceIterator(resultSet));
            claimSet=Database.getPatentVectorData(patentNumbersGroupedByDate,true);
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
        System.out.println("Current Label: "+current.getSecond());
        currentLabel = current.getSecond();
        return current.getFirst();
    }

    @Override
    public boolean hasNext() {
        return (currentPatentIterator==null||currentPatentIterator.hasNext());
    }

    @Override
    public void reset() {
        // check if we already have everything
        if(currentPatentIterator!=null) currentPatentIterator = iter.iterator();
    }

    @Override
    public String currentLabel() {
        return currentLabel;
    }
}
