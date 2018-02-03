package data_pipeline.vectorize;

import data_pipeline.helpers.ShuffleArray;
import lombok.Setter;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

public class FileMinibatchIterator implements DataSetIterator {
    public static final String DEFAULT_PATTERN = "dataset-%d.bin";
    private AtomicInteger currIdx;
    private File rootDir;
    private int totalBatches;
    @Setter
    private DataSetPreProcessor dataSetPreProcessor;
    private final String pattern;
    private int[] shuffledIndices;
    private final List<RecursiveTask<List<DataSet>>> dataSetQueue;
    private Iterator<DataSet> currentIterator;
    private int miniBatch;


    public FileMinibatchIterator(File rootDir, int limit, int miniBatch) {
        this(rootDir, DEFAULT_PATTERN, limit, miniBatch);
    }

    public FileMinibatchIterator(File rootDir, String pattern, int limit, int miniBatch) {
        this.totalBatches = -1;
        this.rootDir = rootDir;
        int numFiles = rootDir.list().length;
        this.totalBatches = limit > 0 ? Math.min(limit,numFiles) : numFiles;
        this.pattern = pattern;
        this.dataSetQueue = new ArrayList<>();
        this.currIdx = new AtomicInteger(0);
        this.miniBatch=miniBatch;
        this.shuffledIndices=new int[totalBatches];
        for(int i = 0; i < totalBatches; i++) {
            shuffledIndices[i]=i;
        }
        ShuffleArray.shuffleArray(shuffledIndices); // randomizes mini batch order
    }

    public DataSet next(int num) {
        throw new UnsupportedOperationException("Unable to load custom number of examples");
    }

    public int totalExamples() {
        throw new UnsupportedOperationException();
    }

    public int inputColumns() {
        throw new UnsupportedOperationException();
    }

    public int totalOutcomes() {
        throw new UnsupportedOperationException();
    }

    public boolean resetSupported() {
        return true;
    }

    public boolean asyncSupported() {
        return false;
    }

    public void reset() {
        this.currIdx.set(0);
        ShuffleArray.shuffleArray(shuffledIndices); // randomizes mini batch order
    }

    public int batch() {
        throw new UnsupportedOperationException();
    }

    public int cursor() {
        return this.currIdx.get();
    }

    public int numExamples() {
        throw new UnsupportedOperationException();
    }

    public void setPreProcessor(DataSetPreProcessor preProcessor) {
        this.dataSetPreProcessor = preProcessor;
    }

    public DataSetPreProcessor getPreProcessor() {
        return this.dataSetPreProcessor;
    }

    public List<String> getLabels() {
        return null;
    }

    public boolean hasNext(){
        int nWorkers = 2;
        while(dataSetQueue.size()<nWorkers && this.currIdx.get()<shuffledIndices.length) {
            final int readIdx = shuffledIndices[this.currIdx.getAndIncrement()];
            RecursiveTask<List<DataSet>> task = new RecursiveTask<List<DataSet>>() {
                @Override
                protected List<DataSet> compute() {
                    try {
                        DataSet e = read(readIdx);
                        if (dataSetPreProcessor != null) {
                            dataSetPreProcessor.preProcess(e);
                        }
                        // split
                        if(miniBatch>0) {
                            return e.batchBy(miniBatch);
                        } else {
                            return Collections.singletonList(e);
                        }
                    } catch (Exception var2) {
                        throw new IllegalStateException("Unable to read dataset");
                    }
                }
            };
            task.fork();
            dataSetQueue.add(task);
        }
        return (currentIterator!=null&&currentIterator.hasNext())||dataSetQueue.size()>0;
    }

    public DataSet next() {
        return nextDataSet();
    }

    private DataSet nextDataSet() {
        if(currentIterator==null||!currentIterator.hasNext()) {
            currentIterator = dataSetQueue.remove(0).join().iterator();
        }
        return currentIterator.next();
    }

    private DataSet read(int idx) throws IOException {
        File path = new File(this.rootDir, String.format(this.pattern, new Object[]{Integer.valueOf(idx)}));
        DataSet d = new DataSet();
        d.load(path);
        return d;
    }
}
