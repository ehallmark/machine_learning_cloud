package data_pipeline.vectorize;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

public class FileMinibatchIterator implements DataSetIterator {
    private static final int FETCH_LIMIT = 2;
    public static final String DEFAULT_PATTERN = "dataset-%d.bin";
    private AtomicInteger currIdx;
    private File rootDir;
    private int totalBatches;
    private DataSetPreProcessor dataSetPreProcessor;
    private final String pattern;
    public FileMinibatchIterator(File rootDir) {
        this(rootDir,-1);
    }

    public FileMinibatchIterator(File rootDir, int limit) {
        this(rootDir, DEFAULT_PATTERN, limit);
    }

    public FileMinibatchIterator(File rootDir, String pattern, int limit) {
        this.totalBatches = -1;
        this.rootDir = rootDir;
        int numFiles = rootDir.list().length;
        this.totalBatches = limit > 0 ? Math.min(limit,numFiles) : numFiles;
        this.pattern = pattern;
        this.currIdx = new AtomicInteger(0);
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

    public boolean hasNext() {
        return this.currIdx.get() < this.totalBatches;
    }

    public DataSet next() {
        return nextDataSet();
    }

    private DataSet nextDataSet() {
        final int readIdx = this.currIdx.getAndIncrement();
        try {
            DataSet e = this.read(readIdx);
            if (this.dataSetPreProcessor != null) {
                this.dataSetPreProcessor.preProcess(e);
            }
            return e;
        } catch (Exception var2) {
            throw new IllegalStateException("Unable to read dataset");
        }
    }

    private DataSet read(int idx) throws IOException {
        File path = new File(this.rootDir, String.format(this.pattern, new Object[]{Integer.valueOf(idx)}));
        DataSet d = new DataSet();
        d.load(path);
        return d;
    }
}
