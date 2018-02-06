package models.similarity_models.combined_similarity_model;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/4/2018.
 */
public class VocabSamplingIterator implements MultiDataSetIterator {
    private final Random rand;
    private AtomicInteger cnt = new AtomicInteger(0);
    private int batchSize;
    private int[] indices;
    private INDArray[] allVectors;
    private INDArray allMasks;
    private int limit;
    public VocabSamplingIterator(int[] indices, INDArray[] allVectors, INDArray allMasks, int limit, int batchSize, boolean randomize) {
        if(limit <= 0) limit = allVectors.length;
        this.allVectors=allVectors;
        this.limit=limit;
        this.allMasks=allMasks;
        this.batchSize=batchSize;
        this.indices=indices;
        this.rand = !randomize ? null : new Random(System.currentTimeMillis());
    }

    @Override
    public MultiDataSet next(int n) {
        int num = Math.min(n,limit-cnt.get());
        INDArray features = Nd4j.create(num,allVectors[0].rows(),allVectors[0].columns());
        int[] maskIndices = new int[num];
        for(int i = 0; i < num; i++) {
            int idx = rand == null ? cnt.get() : indices[rand.nextInt(indices.length)];
            maskIndices[i]=idx;
            features.get(NDArrayIndex.point(idx),NDArrayIndex.all(),NDArrayIndex.all()).assign(allVectors[idx].dup());
            cnt.getAndIncrement();
        }
        INDArray featureMask = Nd4j.pullRows(allMasks,1,maskIndices);
        INDArray[] fArray = new INDArray[]{features};
        INDArray[] mArray = new INDArray[]{featureMask};
        return new org.nd4j.linalg.dataset.MultiDataSet(fArray,fArray,mArray,mArray);
    }


    @Override
    public void setPreProcessor(MultiDataSetPreProcessor multiDataSetPreProcessor) {

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
