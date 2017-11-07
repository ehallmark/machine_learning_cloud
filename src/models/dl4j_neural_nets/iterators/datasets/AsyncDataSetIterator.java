package models.dl4j_neural_nets.iterators.datasets;

import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Evan on 1/15/2017.
 */
public class AsyncDataSetIterator implements DataSetIterator {
    private DataSetIterator iterator;
    private LinkedList<DataSet> queue = new LinkedList<>();
    private List<Thread> threads = new ArrayList<>();
    private int numThreads;
    private final int seekDistance = 4;
    private AtomicBoolean noMoreSequences = new AtomicBoolean(false);
    public AsyncDataSetIterator(DataSetIterator iterator, int numThreads) {
        this.iterator=iterator;
        this.numThreads=numThreads;
        startThreads();
    }

    @Override
    public DataSet next() {
        return next(-1);
    }

    private void startThreads() {
        for(int n = threads.size(); n < numThreads; n++) {
            Runnable thread = () -> {
                int counter = 0;
                while (counter < seekDistance) {
                    DataSet dataSet;
                    synchronized (iterator) {
                        boolean hasMoreSequences = iterator.hasNext();
                        if(!hasMoreSequences) {
                            noMoreSequences.set(true);
                            break;
                        }
                        dataSet=iterator.next();
                    }
                    synchronized (queue) {
                        queue.add(dataSet);
                    }
                    counter++;
                }
            };
            Thread t = new Thread(thread);
            t.start();
            threads.add(t);
        }
    }

    @Override
    public DataSet next(int i) {
        synchronized (queue) {
            return queue.removeFirst();
        }
    }

    @Override
    public int totalExamples() {
        return iterator.totalExamples();
    }

    @Override
    public int inputColumns() {
        return iterator.inputColumns();
    }

    @Override
    public int totalOutcomes() {
        return iterator.totalOutcomes();
    }

    @Override
    public boolean resetSupported() {
        return iterator.resetSupported();
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        noMoreSequences.set(false);
        threads.clear();
        iterator.reset();
        queue.clear();
        startThreads();
    }

    @Override
    public int batch() {
        return iterator.batch();
    }

    @Override
    public int cursor() {
        return iterator.cursor();
    }

    @Override
    public int numExamples() {
        return iterator.numExamples();
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
        iterator.setPreProcessor(dataSetPreProcessor);
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return iterator.getPreProcessor();
    }

    @Override
    public List<String> getLabels() {
        return iterator.getLabels();
    }

    @Override
    public boolean hasNext() {
        boolean isEmpty;
        synchronized (queue) {
            if(!queue.isEmpty()) return true;
        }

        if(noMoreSequences.get()) {
            threads.forEach(thread->{
                try {
                    thread.join();
                }catch(Exception e) {
                    e.printStackTrace();
                }
            });
            threads.clear();
        } else {
            while(!threads.isEmpty()) {
                try {
                    threads.remove(0).join();
                } catch(Exception e) {
                    e.printStackTrace();
                }
                if(!queue.isEmpty()) {
                    break;
                }
            }
            if(!noMoreSequences.get()) startThreads();
            return hasNext();
        }
        isEmpty = queue.isEmpty();
        return !isEmpty;
    }

}
