package data_pipeline.vectorize;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

import data_pipeline.helpers.ShuffleArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

public class FileMinibatchIterator implements DataSetIterator {
    private static final boolean DEFAULT_ASYNC = false;
    public static final String DEFAULT_PATTERN = "dataset-%d.bin";
    private AtomicInteger currIdx;
    private File rootDir;
    private int totalBatches;
    private DataSetPreProcessor dataSetPreProcessor;
    private final String pattern;
    private boolean async;
    private int[] shuffledIndices;
    private final List<RecursiveTask<DataSet>> dataSetQueue;
    public FileMinibatchIterator(File rootDir) {
        this(rootDir,DEFAULT_ASYNC);
    }

    public FileMinibatchIterator(File rootDir, boolean async) {
        this(rootDir,-1,async);
    }

    public FileMinibatchIterator(File rootDir, int limit) {
        this(rootDir,limit,DEFAULT_ASYNC);
    }

    public FileMinibatchIterator(File rootDir, int limit, boolean async) {
        this(rootDir, DEFAULT_PATTERN, limit,async);
    }

    public FileMinibatchIterator(File rootDir, String pattern, int limit, boolean async) {
        this.totalBatches = -1;
        this.rootDir = rootDir;
        int numFiles = rootDir.list().length;
        this.totalBatches = limit > 0 ? Math.min(limit,numFiles) : numFiles;
        this.pattern = pattern;
        this.dataSetQueue = new ArrayList<>();
        this.currIdx = new AtomicInteger(0);
        this.async=async;
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
        return async;
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
        int nWorkers = 4;
        while(dataSetQueue.size()<nWorkers && this.currIdx.get()<shuffledIndices.length) {
            final int readIdx = shuffledIndices[this.currIdx.getAndIncrement()];
            RecursiveTask<DataSet> task = new RecursiveTask<DataSet>() {
                @Override
                protected DataSet compute() {
                    try {
                        DataSet e = read(readIdx);
                        if (dataSetPreProcessor != null) {
                            dataSetPreProcessor.preProcess(e);
                        }
                        return e;
                    } catch (Exception var2) {
                        throw new IllegalStateException("Unable to read dataset");
                    }
                }
            };
            task.fork();
            dataSetQueue.add(task);
        }
        return dataSetQueue.size()>0;
    }

    public DataSet next() {
        return nextDataSet();
    }

    private DataSet nextDataSet() {
        return dataSetQueue.remove(0).join();
    }

    private DataSet read(int idx) throws IOException {
        File path = new File(this.rootDir, String.format(this.pattern, new Object[]{Integer.valueOf(idx)}));
        DataSet d = new DataSet();
        d.load(path);
        return d;
    }
}
