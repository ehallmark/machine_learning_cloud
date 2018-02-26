package models.similarity_models.combined_similarity_model;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/27/17.
 */
public class CombinedWord2VecToCPCIterator implements MultiDataSetIterator {
    private static final Random rand = new Random(235);
    private SequenceIterator<VocabWord> documentIterator;
    private int batch;
    private MultiDataSet currentDataSet;
    private DeepCPCVariationalAutoEncoderNN net2;
    private Map<String,INDArray> wordCpcVectors;
    public CombinedWord2VecToCPCIterator(SequenceIterator<VocabWord> documentIterator, Map<String,INDArray> wordCpcVectors, DeepCPCVariationalAutoEncoderNN net2, int batchSize) {
        this.documentIterator=documentIterator;
        this.wordCpcVectors=wordCpcVectors;
        this.net2=net2;
        this.batch=batchSize;
        reset();
    }

    @Override
    public MultiDataSet next(int batch) {
        return currentDataSet;
    }

    @Override
    public void setPreProcessor(MultiDataSetPreProcessor multiDataSetPreProcessor) {

    }

    public int inputColumns1() {
        return wordCpcVectors.values().stream().findAny().get().length();
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
        documentIterator.reset();
    }

    public int batch() {
        return batch;
    }

    @Override
    public boolean hasNext() {
        currentDataSet=null;
        int idx = 0;
        INDArray features1 = Nd4j.create(batch,inputColumns1());
        List<String> assets = new ArrayList<>(batch);
        while(documentIterator.hasMoreSequences()&&idx<batch) {
            Sequence<VocabWord> document = documentIterator.nextSequence();
            List<String> sequence = document.getElements().stream().map(e->e.getLabel()).collect(Collectors.toCollection(ArrayList::new));
            if(sequence.size()==0||document.getSequenceLabels().isEmpty()) continue;

            String label = document.getSequenceLabels().get(0).getLabel();
            if(!net2.getCPCMap().containsKey(label)) continue;

            Collections.shuffle(sequence, rand);

            for(VocabWord elem : document.getElements()) {
                if(idx>=batch) break;

                INDArray featureVec = wordCpcVectors.get(elem.getLabel());
                if(featureVec==null) continue;

                assets.add(label);
                features1.get(NDArrayIndex.point(idx),NDArrayIndex.all()).assign(featureVec);
                idx++;
            }
        }

        if(idx>0) {
            if(idx == batch) {
                INDArray vae2 = net2.encode(assets);
                System.out.println("Shape1: "+Arrays.toString(features1.shape()));
                System.out.println("Shape2: "+Arrays.toString(vae2.shape()));
                if(vae2.shape()[0]!=features1.shape()[0]) {
                    throw new RuntimeException("Features must have the same number of examples...");
                }
                INDArray[] allFeatures = new INDArray[]{features1,vae2};
                currentDataSet = new MultiDataSet(allFeatures, allFeatures);
            }
        }
        return currentDataSet!=null;
    }

    @Override
    public MultiDataSet next() {
        return next(batch());
    }
}
