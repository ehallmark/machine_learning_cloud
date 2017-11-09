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
    private static final int FETCH_LIMIT = 3;
    public static final String DEFAULT_PATTERN = "dataset-%d.bin";
    private AtomicInteger currIdx;
    private File rootDir;
    private int totalBatches;
    private DataSetPreProcessor dataSetPreProcessor;
    private final String pattern;
    private ArrayBlockingQueue<RecursiveTask<DataSet>> queue;
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
        this.queue=new ArrayBlockingQueue<>(FETCH_LIMIT);
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
        boolean hasNext = this.currIdx.get() < this.totalBatches;
        boolean originalHasNext = hasNext;
        while(hasNext && queue.size()<FETCH_LIMIT) {
            try {
                RecursiveTask<DataSet> fetchTask = nextFetchTask();
                fetchTask.fork();
                queue.put(fetchTask);
            } catch(Exception e) {
                throw new IllegalStateException("Getting next fetch task.");
            }
            hasNext = this.currIdx.get() < this.totalBatches;
        }
        return originalHasNext;
    }

    public DataSet next() {
        return queue.poll().join();
    }

    private RecursiveTask<DataSet> nextFetchTask() {
        final int readIdx = this.currIdx.getAndIncrement();
        return new RecursiveTask<DataSet>() {
            @Override
            protected DataSet compute() {
                try {
                    DataSet e = FileMinibatchIterator.this.read(readIdx);
                    if (FileMinibatchIterator.this.dataSetPreProcessor != null) {
                        FileMinibatchIterator.this.dataSetPreProcessor.preProcess(e);
                    }
                    return e;
                } catch (Exception var2) {
                    throw new IllegalStateException("Unable to read dataset");
                }
            }
        };
    }

    private DataSet read(int idx) throws IOException {
        File path = new File(this.rootDir, String.format(this.pattern, new Object[]{Integer.valueOf(idx)}));
        DataSet d = new DataSet();
        d.load(path);
        return d;
    }
}
