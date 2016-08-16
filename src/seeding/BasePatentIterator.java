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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements LabelAwareDocumentIterator {

    protected final int startDate;
    protected Iterator<Pair<InputStream,String>> currentPatentIterator;
    protected SentencePreProcessor preProcessor;
    protected String currentLabel;
    protected List<Pair<InputStream,String>> iter;
    protected List<String[]> dateList;
    protected int n = 0;
    protected AtomicInteger cnt;
    protected Iterator<String[]> dateIter;
    // used to tag each sequence with own Id

    public BasePatentIterator(int startDate) throws SQLException {
        this.startDate=startDate;
        preProcessor=new MyPreprocessor();
        dateList=new ArrayList<>();
        ResultSet rs = Database.getPatentsBetween(Constants.START_DATE);
        while(rs.next()) {
            dateList.add((String[])rs.getArray(1).getArray());
            n+=dateList.get(dateList.size()-1).length;
        }

    }


    private List<Pair<InputStream,String>> processedSentenceIterator(ResultSet rs) throws SQLException {
        List<Pair<InputStream,String>> toReturn = new ArrayList<>();
        while(rs.next()) {
            String text = rs.getString(2);
            String label = rs.getString(1);
            if(VectorHelper.shouldRemoveSentence(text)||label==null) continue;
            toReturn.add(new Pair<>(new ByteArrayInputStream(text.getBytes()),label.replaceAll("\\s+","")));
        }
        return toReturn;
    }

    public Pair<InputStream,String> nextSentence() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                int currentCnt = cnt.getAndIncrement();
                if(currentCnt%1000==0) {
                    long time = System.currentTimeMillis();
                    System.out.println("Estimated time remaining: "+((new Double(n-currentCnt)/currentCnt)*(time)/(60*60*1000))+" hours");
                }
                return currentPatentIterator.next();
            }
            ResultSet resultSet;
            ResultSet claimSet;
            iter = new ArrayList<>();
            // Check for more results in result set
            resultSet=Database.getPatentVectorData(dateIter.next(),false);
            iter.addAll(processedSentenceIterator(resultSet));
            claimSet=Database.getPatentVectorData(dateIter.next(),true);
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
        //System.out.println("Current Label: "+current.getSecond());
        currentLabel = current.getSecond();
        return current.getFirst();
    }

    @Override
    public boolean hasNext() {
        return (currentPatentIterator==null||currentPatentIterator.hasNext()||dateIter.hasNext());
    }

    @Override
    public void reset() {
        // check if we already have everything
        dateIter = dateList.iterator();
        currentPatentIterator=null;
        cnt = new AtomicInteger(0);
    }

    @Override
    public String currentLabel() {
        return currentLabel;
    }
}
