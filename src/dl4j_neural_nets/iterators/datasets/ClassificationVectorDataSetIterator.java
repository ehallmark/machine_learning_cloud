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
public class ClassificationVectorDataSetIterator implements DataSetIterator {
    private int numInputs;
    private int numOutputs;
    private List<String> patents;
    private Iterator<String> patentIterator;
    private int batchSize;
    private INDArray vector1;
    private INDArray vector2;
    private Map<String,INDArray> lookupTable1;
    private Map<String,INDArray> lookupTable2;

    // Concatenates vectors for all provided weight lookup tables
    public ClassificationVectorDataSetIterator(List<String> patents, Map<String,INDArray> lookupTable1, Map<String,INDArray> lookupTable2, int numInputs, int numOutputs, int batchSize) {
        this.numOutputs=numOutputs;
        this.numInputs=numInputs;
        this.patents=patents;
        this.lookupTable1=lookupTable1;
        this.lookupTable2=lookupTable2;
        this.batchSize=batchSize;
        this.vector1 = Nd4j.create(batchSize,numInputs);
        this.vector2 = Nd4j.create(batchSize,numOutputs);
        setupIterator();
    }

    private void setupIterator() {
        Collections.shuffle(patents);
        patentIterator=patents.iterator();
    }

    @Override
    public DataSet next(int n) {
        return new DataSet(vector1,vector2);
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
        while(patentIterator.hasNext() && i.getAndIncrement()<batchSize) {
            String next = patentIterator.next();
            INDArray vec1 =  lookupTable1.get(next);
            INDArray vec2 =  lookupTable2.get(next);
            vector1.putRow(i.get()-1,vec1);
            vector2.putRow(i.get()-1,vec2);
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
