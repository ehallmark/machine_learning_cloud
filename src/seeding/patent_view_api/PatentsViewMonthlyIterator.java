package seeding.patent_view_api;

import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.documentiterator.LabelsSource;

import java.time.LocalDate;
import java.util.*;

/**
 * Created by Evan on 3/27/2017.
 */
public class PatentsViewMonthlyIterator implements LabelAwareIterator {
    private Queue<LabelledDocument> queue;
    private Iterator<LocalDate> datesIter;
    private List<LocalDate> dates;
    private static List<LocalDate> ALL_MONTHS;
    static {
        ALL_MONTHS=new ArrayList<>();
        LocalDate date = LocalDate.now().minusYears(20).withDayOfMonth(1);
        LocalDate now = LocalDate.now();
        while(date.isBefore(now)) {
            ALL_MONTHS.add(date.plusYears(1));
            date=date.plusYears(1);
        }
    }
    public PatentsViewMonthlyIterator(List<LocalDate> dates) {
        this.queue = new ArrayDeque<>();
        this.dates=dates;
        this.datesIter=dates.iterator();
    }
    @Override
    public boolean hasNextDocument() {
        return hasNext();
    }

    @Override
    public LabelledDocument nextDocument() {
        return null;
    }

    @Override
    public void reset() {
        datesIter=dates.iterator();
    }

    @Override
    public LabelsSource getLabelsSource() {
        return null;
    }

    @Override
    public void shutdown() {

    }

    @Override
    public boolean hasNext() {
        return queue.size()>0||datesIter.hasNext();
    }

    @Override
    public LabelledDocument next() {
        return queue.poll();
    }
}
