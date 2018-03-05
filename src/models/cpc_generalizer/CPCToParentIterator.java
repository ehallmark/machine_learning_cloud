package models.cpc_generalizer;

import cpc_normalization.CPCHierarchy;
import models.similarity_models.combined_similarity_model.AbstractEncodingModel;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/27/17.
 */
public class CPCToParentIterator implements MultiDataSetIterator {
    private Random random = new Random(2352);
    private List<String> cpcList;
    private AtomicInteger cnt = new AtomicInteger(0);
    private Word2Vec word2Vec;
    private int batch;
    private CPCHierarchy hierarchy;
    private final int n;
    private double[] cpcProbs;
    private MultiDataSetPreProcessor preProcessor;
    public CPCToParentIterator(List<String> cpcList, double[] cpcProbs, CPCHierarchy hierarchy, Word2Vec word2Vec, int batchSize, int n) {
        this.cpcList=cpcList;
        this.hierarchy=hierarchy;
        this.n=n;
        this.word2Vec=word2Vec;
        this.batch=batchSize;
        this.cpcProbs=cpcProbs;
        reset();
    }

    private String sample() {
        if(cpcProbs==null) {
            return cpcList.get(cnt.get());
        } else {
          //  System.out.print("-");
            double s = 0d;
            double r = random.nextDouble();
            for(int i = 0; i < cpcProbs.length; i++) {
                s+=cpcProbs[i];
                if(s>=r) {
                    return cpcList.get(i);
                }
            }
            return cpcList.get(cpcList.size()-1);
        }
    }

    @Override
    public MultiDataSet next(int batch) {
        int idx = 0;
        List<String> features = new ArrayList<>(batch);
        List<String> labels = new ArrayList<>(batch);
        while(idx<batch&&cnt.get()<n) {
            String cpc = sample();
            String parent = hierarchy.getLabelToCPCMap().get(cpc).getParent().getName();
            features.add(cpc);
            labels.add(parent);
            idx++;
            cnt.getAndIncrement();
        }

        if(idx>0) {
            INDArray allFeatures = word2Vec.getWordVectors(features);
            INDArray allLabels = word2Vec.getWordVectors(labels);
            System.out.println("Shape1: "+ Arrays.toString(allFeatures.shape()));
            System.out.println("Shape2: "+Arrays.toString(allLabels.shape()));
            MultiDataSet ds = new MultiDataSet(new INDArray[]{allFeatures}, new INDArray[]{allLabels});
            if(preProcessor!=null) {
                preProcessor.preProcess(ds);
            }
            return ds;
        }
        return null;
    }

    @Override
    public void setPreProcessor(MultiDataSetPreProcessor multiDataSetPreProcessor) {
        this.preProcessor=multiDataSetPreProcessor;
    }

    @Override
    public MultiDataSetPreProcessor getPreProcessor() {
        return preProcessor;
    }

    public int inputColumns() {
        return word2Vec.getLayerSize();
    }

    public int totalOutcomes() {
        return word2Vec.getLayerSize();
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

    public int batch() {
        return batch;
    }

    @Override
    public boolean hasNext() {
        return cnt.get()<n;
    }

    @Override
    public MultiDataSet next() {
        return next(batch());
    }

}
