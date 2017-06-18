package dl4j_neural_nets.iterators.datasets;

import graphical_models.classification.CPCKMeans;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/14/16.
 */
public class CPCVectorDataSetIterator implements DataSetIterator {
    private int numInputs;
    private int numOutputs;
    private List<String> patents;
    private Iterator<String> patentIterator;
    private int batchSize;
    private INDArray vector;
    private Map<String,INDArray> lookupTable;

    // Concatenates vectors for all provided weight lookup tables
    public CPCVectorDataSetIterator(List<String> patents, Map<String,INDArray> lookupTable, int numInputs, int batchSize) {
        this.numOutputs=numInputs;
        this.numInputs=numInputs;
        this.patents=patents;
        this.lookupTable=lookupTable;
        this.batchSize=batchSize;
        this.vector = Nd4j.create(batchSize,numInputs);
        setupIterator();
    }

    private void setupIterator() {
        Collections.shuffle(patents);
        patentIterator=patents.iterator();
    }

    @Override
    public DataSet next(int n) {
        return new DataSet(vector,vector);
    }

    @Override
    public int totalExamples() {
       return patents.size();
    }

    @Override
    public int inputColumns() {
        return numInputs;
    }

    @Override
    public int totalOutcomes() {
        return numOutputs;
    }

    public boolean resetSupported() {
        return true;
    }

    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
        setupIterator();
    }

    @Override
    public int batch() {
        return batchSize;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int numExamples() {
        return patents.size();
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() {
        boolean hasNext = true;
        AtomicInteger i = new AtomicInteger(0);
        System.out.println("Shape of vector: "+vector.rows()+"x"+vector.columns());
        while(patentIterator.hasNext() && i.getAndIncrement()<batchSize) {
            INDArray vec =  lookupTable.get(patentIterator.next());
            System.out.println("Shape of Lookup: "+vec.rows()+"x"+vec.columns());
            vector.putRow(i.get()-1,vec);
        }
        if(batchSize-i.get()>0) { // ran out of patents
            hasNext = false;
        }
        return hasNext;
    }

    @Override
    public DataSet next() {
        return next(batchSize);
    }
}
