package seeding;

import opennlp.tools.parser.Cons;
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
    protected Iterator<Pair<InputStream,String>> currentPatentIterator;
    protected SentencePreProcessor preProcessor;
    protected String currentLabel;
    protected List<Pair<InputStream,String>> iter;
    // used to tag each sequence with own Id

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
        preProcessor=new MyPreprocessor();
    }


    private List<Pair<InputStream,String>> processedSentenceIterator(ResultSet rs) throws SQLException {
        List<Pair<InputStream,String>> toReturn = new ArrayList<>();
        while(rs.next()) {
            String text = rs.getString(2);
            String label = rs.getString(1);
            System.out.println(label);
            if(VectorHelper.shouldRemoveSentence(text)) continue;
            toReturn.add(new Pair<>(new ByteArrayInputStream(text.getBytes()),label));
        }
        return toReturn;
    }

    public Pair<InputStream,String> nextSentence() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                return currentPatentIterator.next();
            }
            ResultSet resultSet;
            ResultSet claimSet;
            iter = new ArrayList<>();
            // Check for more results in result set
            ResultSet rs = Database.getPatentsBetween(Constants.START_DATE);
            while(rs.next()) {
                String[] data = (String[])rs.getArray(1).getArray();
                resultSet=Database.getPatentVectorData(data,false);
                iter.addAll(processedSentenceIterator(resultSet));
                claimSet=Database.getPatentVectorData(data,true);
                iter.addAll(processedSentenceIterator(claimSet));
            }


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
