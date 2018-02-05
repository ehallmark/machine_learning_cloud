package models.similarity_models.combined_similarity_model;

import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/4/2018.
 */
public class VocabSamplingIterator implements MultiDataSetIterator {
    private Word2Vec word2Vec;
    private List<String> labels;
    private final int limit;
    private final Random rand;
    private AtomicInteger cnt = new AtomicInteger(0);
    private int batchSize;
    private double[] probabilities;
    public VocabSamplingIterator(Word2Vec word2Vec, List<String> labels, double[] probabilities, int batchSize, int limit, boolean testing) {
        this.word2Vec=word2Vec;
        this.probabilities=probabilities;
        this.labels=labels;
        this.batchSize=batchSize;
        this.limit=limit;
        this.rand = new Random(testing ? 69 : System.currentTimeMillis());
    }

    @Override
    public MultiDataSet next(int n) {
        int num = Math.min(n,limit-cnt.get());
        int[] indices = new int[num];
        for(int i = 0; i < num; i++) {
            cnt.getAndIncrement();
            indices[i] = sample();
        }
        INDArray features = Nd4j.pullRows(word2Vec.getLookupTable().getWeights(), 1, indices);
        return new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{features},new INDArray[]{features});
    }

    public int sample() {
        Integer sample = null;
        int notFoundCount = 0;
        while(sample==null&&notFoundCount<100) {
            double r = rand.nextDouble();
            double p = 0d;
            for(int i = 0; i < labels.size(); i++) {
                p+=probabilities[i];
                if(p>=r) {
                    sample = word2Vec.getVocab().indexOf(labels.get(i));
                    break;
                }
            }
            System.out.println("Warning no sampling found...");
            notFoundCount++;
        }
        if(sample==null) {
            throw new RuntimeException("Sample is null. Please check that probabilities are valid (i.e. sum to 1.0).");
        }
        return sample;
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
