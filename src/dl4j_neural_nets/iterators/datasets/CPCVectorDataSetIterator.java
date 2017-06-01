package dl4j_neural_nets.iterators.datasets;

import graphical_models.classification.CPCKMeans;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/14/16.
 */
public class CPCVectorDataSetIterator implements DataSetIterator {
    private static Random random = new Random(41);
    private int numInputs;
    private int numOutputs;
    private List<String> orderedClassifications;
    private List<String> patents;
    private Iterator<String> patentIterator;
    private int batchSize;
    private DataSet nextDataSet;

    // Concatenates vectors for all provided weight lookup tables
    public CPCVectorDataSetIterator(List<String> patents, List<String> orderedClassifications, int batchSize) {
        this.numOutputs=orderedClassifications.size();
        this.numInputs=orderedClassifications.size();
        this.patents=patents;
        this.orderedClassifications=orderedClassifications;
        this.batchSize=batchSize;
        setupIterator();
    }

    private void setupIterator() {
        Collections.shuffle(patents);
        nextDataSet=null;
        patentIterator=patents.iterator();
    }

    @Override
    public DataSet next(int n) {
        return nextDataSet;
    }

    @Override
    public int totalExamples() {
        throw new UnsupportedOperationException();
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
        return true;
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
        throw new UnsupportedOperationException();
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
        INDArray inputs = Nd4j.create(batchSize,numInputs);
        AtomicInteger i = new AtomicInteger(0);
        while(patentIterator.hasNext() && i.getAndIncrement()<batchSize) {
            inputs.putRow(i.get()-1, Nd4j.create(CPCKMeans.classVectorForPatents(Arrays.asList(patentIterator.next()),orderedClassifications)));
        }
        if(i.get()>0) {
            while (i.getAndIncrement() < batchSize) {
                inputs.putRow(i.get() - 1, Nd4j.zeros(orderedClassifications.size()));
            }
            nextDataSet = new DataSet(inputs, inputs);
        } else {
            nextDataSet=null;
        }
        return nextDataSet!=null;
    }

    @Override
    public DataSet next() {
        return next(batchSize);
    }
}
