package models.iterators;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Created by ehallmark on 12/13/17.
 */
public class AbstractAssetDataSetIterator implements DataSetIterator {
    private final Function<String,INDArray> featuresFromAssetFunction;
    private final Function<String,INDArray> labelsFromAssetFunction;
    private final List<String> assets;
    private Iterator<String> iterator;
    private final boolean shuffle;
    private final int nInputs;
    private final int nOutputs;
    private final int batch;
    public AbstractAssetDataSetIterator(List<String> assets, Function<String,INDArray> featuresFromAssetFunction, Function<String,INDArray> labelsFromAssetFunction, int nInputs, int nOutputs, int batchSize, boolean shuffle) {
        this.featuresFromAssetFunction=featuresFromAssetFunction;
        this.labelsFromAssetFunction=labelsFromAssetFunction;
        this.batch=batchSize;
        this.assets = assets;
        this.shuffle=shuffle;
        this.nInputs=nInputs;
        this.nOutputs=nOutputs;
        reset();
    }

    @Override
    public DataSet next(int n) {
        INDArray features = Nd4j.create(n,nInputs);
        INDArray labels = Nd4j.create(n,nOutputs);

        int i;
        for(i = 0; i < n && iterator.hasNext(); i++) {
            String asset = iterator.next();

            INDArray feature = featuresFromAssetFunction.apply(asset);
            INDArray label = labelsFromAssetFunction.apply(asset);

            if(feature != null && label != null) {
                features.putRow(i,feature);
                labels.putRow(i,label);
            } else {
                i--;
            }
        }

        if (i == 0) {
            System.out.println("WARNING: i == 0. Returning null");
            return null;
        }

        if(i < n) {
            features = features.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
            labels = labels.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
        }
        return new DataSet(features,labels);
    }

    @Override
    public int totalExamples() {
        return numExamples();
    }

    @Override
    public int inputColumns() {
        return nInputs;
    }

    @Override
    public int totalOutcomes() {
        return nOutputs;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return true;
    }

    @Override
    public void reset() {
        if(shuffle) Collections.shuffle(assets, new Random(2362));
        this.iterator = assets.iterator();
    }

    @Override
    public int batch() {
        return batch;
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException("cursor()");
    }

    @Override
    public int numExamples() {
        return assets.size();
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
        throw new UnsupportedOperationException("setPreProcessor()");
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        throw new UnsupportedOperationException("getPreProcessor()");
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException("getLabels()");
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public DataSet next() {
        return next(batch());
    }
}
