package data_pipeline.vectorize;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

public class FileMinibatchIterator implements DataSetIterator {
    public static final String DEFAULT_PATTERN = "dataset-%d.bin";
    private int currIdx;
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
        this.totalBatches = limit > 0 ? limit : rootDir.list().length;
        this.pattern = pattern;
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
        return true;
    }

    public void reset() {
        this.currIdx = 0;
    }

    public int batch() {
        throw new UnsupportedOperationException();
    }

    public int cursor() {
        return this.currIdx;
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
        return this.currIdx < this.totalBatches;
    }

    public void remove() {
    }

    public DataSet next() {
        try {
            DataSet e = this.read(this.currIdx);
            if(this.dataSetPreProcessor != null) {
                this.dataSetPreProcessor.preProcess(e);
            }

            ++this.currIdx;
            return e;
        } catch (IOException var2) {
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
