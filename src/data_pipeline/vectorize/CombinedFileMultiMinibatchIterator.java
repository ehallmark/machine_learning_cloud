package data_pipeline.vectorize;

import data_pipeline.helpers.ShuffleArray;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CombinedFileMultiMinibatchIterator implements MultiDataSetIterator{
    private MultiDataSetPreProcessor dataSetPreProcessor;
    private MultiDataSetIterator d1;
    private MultiDataSetIterator d2;
    private static Random rand = new Random(2352);
    public CombinedFileMultiMinibatchIterator(MultiDataSetIterator d1, MultiDataSetIterator d2) {
        this.d1 = d1;
        this.d2 = d2;
    }

    @Override
    public void setPreProcessor(MultiDataSetPreProcessor multiDataSetPreProcessor) {
        this.dataSetPreProcessor=multiDataSetPreProcessor;
    }

    public MultiDataSet next(int num) {
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
        d1.reset();
        d2.reset();
    }

    public int batch() {
        throw new UnsupportedOperationException("batch()");
    }

    public int cursor() {
        throw new UnsupportedOperationException("cursor()");
    }

    public int numExamples() {
        throw new UnsupportedOperationException("numExamples()");
    }

    public List<String> getLabels() {
        return null;
    }

    public boolean hasNext() {
        return (d1.hasNext()&&d2.hasNext());
    }

    public MultiDataSet next() {
        return nextDataSet();
    }

    private MultiDataSet nextDataSet() {
        MultiDataSet ds;
        if(rand.nextBoolean()) {
            ds = d1.next();
        } else {
            ds = d2.next();
        }
        if(dataSetPreProcessor!=null) {
            dataSetPreProcessor.preProcess(ds);
        }
        return ds;
    }

}
