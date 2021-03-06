package models.similarity_models.combined_similarity_model;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by Evan on 2/4/2018.
 */
public class VocabSamplingIterator implements MultiDataSetIterator {
    private final Random rand;
    private AtomicInteger cnt = new AtomicInteger(0);
    private int batchSize;
    private int limit;
    private int[] indices;
    private Function<Integer,INDArray> vectorFunction;
    private int vectorSize;
    private int maxSamples;
    private MultiDataSetPreProcessor preProcessor;
    public VocabSamplingIterator(int[] indices, Function<Integer,INDArray> vectorFunction, int vectorSize, int maxSamples, int limit, int batchSize, boolean randomize) {
        if(limit <= 0) limit = indices.length;
        this.vectorFunction=vectorFunction;
        this.limit=limit;
        this.vectorSize=vectorSize;
        this.maxSamples=maxSamples;
        this.indices=indices;
        this.batchSize=batchSize;
        this.rand = !randomize ? null : new Random(System.currentTimeMillis());
    }

    @Override
    public MultiDataSet next(int n) {
        int num = Math.min(n,limit-cnt.get());
        INDArray features = Nd4j.create(num,vectorSize,maxSamples);
        for(int i = 0; i < num; i++) {
            int idx = rand == null ? cnt.get() : rand.nextInt(indices.length);
            features.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.all()).assign(vectorFunction.apply(idx));
            cnt.getAndIncrement();
        }
        INDArray[] fArray = new INDArray[]{features};
        MultiDataSet ds = new org.nd4j.linalg.dataset.MultiDataSet(fArray,fArray);
        if(preProcessor!=null) {
            preProcessor.preProcess(ds);
        }
        return ds;
    }


    @Override
    public void setPreProcessor(MultiDataSetPreProcessor multiDataSetPreProcessor) {
        this.preProcessor=preProcessor;
    }

    @Override
    public MultiDataSetPreProcessor getPreProcessor() {
        return preProcessor;
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public void reset() {
         cnt.set(0);
    }

    @Override
    public boolean hasNext() {
        return cnt.get()<limit;
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }
}
