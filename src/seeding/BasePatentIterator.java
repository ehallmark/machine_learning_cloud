package seeding;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import tools.VectorHelper;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class BasePatentIterator implements LabelAwareSentenceIterator {

    protected final int startDate;
    protected Iterator<Pair<String,String>> currentPatentIterator;
    protected SentencePreProcessor preProcessor;
    protected String currentLabel;
    protected List<Pair<String,String>> iter;
    protected List<String[]> dateList;
    protected int n = 0;
    protected AtomicInteger cnt;
    protected Iterator<String[]> dateIter;
    protected long lastTime;
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


    private List<Pair<String,String>> processedSentenceIterator(ResultSet rs) throws SQLException {
        List<Pair<String,String>> toReturn = new ArrayList<>();
        while(rs.next()) {
            String text = rs.getString(2);
            String label = rs.getString(1);
            if(text==null||label==null||VectorHelper.shouldRemoveSentence(text)) continue;
            toReturn.add(new Pair<>(text,label.replaceAll("\\s+","")));
        }
        return toReturn;
    }

    @Override
    public String nextSentence() {
        try {
            // Check patent iterator
            if(currentPatentIterator!=null && currentPatentIterator.hasNext()) {
                int currentCnt = cnt.getAndIncrement();
                if(currentCnt%1000==999) {
                    long time = System.currentTimeMillis();
                    System.out.println("Time to complete 1000 patents: "+new Double(time-lastTime)/(1000)+" seconds");
                    lastTime = time;
                    cnt.set(0);
                }
                Pair<String,String> current = currentPatentIterator.next();
                //System.out.println("Current Label: "+current.getSecond());
                currentLabel = current.getSecond();
                return current.getFirst();
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
    public boolean hasNext() {
        return (currentPatentIterator==null||currentPatentIterator.hasNext()||dateIter.hasNext());
    }

    @Override
    public void reset() {
        // check if we already have everything
        dateIter = dateList.iterator();
        currentPatentIterator=null;
        cnt = new AtomicInteger(0);
        lastTime = System.currentTimeMillis();
    }

    @Override
    public void finish() {

    }

    @Override
    public SentencePreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public void setPreProcessor(SentencePreProcessor preProcessor) {

    }

    @Override
    public String currentLabel() {
        return currentLabel;
    }

    @Override
    public List<String> currentLabels() {
        return null;
    }
}
