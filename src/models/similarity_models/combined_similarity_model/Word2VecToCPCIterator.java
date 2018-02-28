package models.similarity_models.combined_similarity_model;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/27/17.
 */
public class Word2VecToCPCIterator implements MultiDataSetIterator {
    public static final Function<List<VocabWord>,Map<String,Integer>> groupingBOWFunction = sequence -> {
        return sequence.stream().collect(Collectors.groupingBy(word->word.getLabel(), Collectors.summingInt(label->(int)label.getElementFrequency())));
    };


    private SequenceIterator<VocabWord> documentIterator;
    private Word2Vec word2Vec;
    private int batch;
    private MultiDataSet currentDataSet;
    private int maxSamples;
    private Map<String,INDArray> vaePredictions;
    public Word2VecToCPCIterator(SequenceIterator<VocabWord> documentIterator, Word2Vec word2Vec, Map<String,INDArray> vaePredictions, int batchSize, int maxSamples) {
        this.documentIterator=documentIterator;
        this.vaePredictions=vaePredictions;
        this.word2Vec=word2Vec;
        this.maxSamples=maxSamples;
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
        documentIterator.reset();
    }

    public int batch() {
        return batch;
    }

    @Override
    public boolean hasNext() {
        currentDataSet=null;
        int idx = 0;
        INDArray features = Nd4j.create(batch,inputColumns(),maxSamples);
        List<String> assets = new ArrayList<>(batch);
        while(documentIterator.hasMoreSequences()&&idx<batch) {
            Sequence<VocabWord> document = documentIterator.nextSequence();
            List<String> sequence = document.getElements().stream().map(e->e.getLabel()).collect(Collectors.toList());

            if(sequence.size()==0) continue;

            String label = document.getSequenceLabels().get(0).getLabel();
            if(!vaePredictions.containsKey(label)) continue;

            INDArray featureVec;
            {
                List<Integer> indexes = new ArrayList<>();
                for (int i = 0; i < sequence.size(); i++) {
                    String l = sequence.get(i);
                    if (word2Vec.getVocab().containsWord(l)) {
                        indexes.add(word2Vec.getVocab().indexOf(l));
                        if(indexes.size()==maxSamples) {
                            break;
                        }
                    }
                }


                if (indexes.size() == 0) {
                    System.out.print("no indices...");
                    continue;
                }

                featureVec = Nd4j.pullRows(word2Vec.getLookupTable().getWeights(),1,indexes.stream().limit(maxSamples).mapToInt(i->i).toArray());


            }
            if(featureVec.rows()<maxSamples) continue;

            assets.add(label);
            features.get(NDArrayIndex.point(idx),NDArrayIndex.all(),NDArrayIndex.interval(0,featureVec.rows())).assign(featureVec.transpose());
            idx++;
        }

        if(idx>0) {
            if(idx == batch) {
                INDArray vae2 = Nd4j.vstack(assets.stream().map(asset->vaePredictions.get(asset)).collect(Collectors.toList()));
                System.out.println("Shape1: "+ Arrays.toString(features.shape()));
                System.out.println("Shape2: "+Arrays.toString(vae2.shape()));
                currentDataSet = new MultiDataSet(new INDArray[]{features}, new INDArray[]{vae2});
            }
        }
        return currentDataSet!=null;
    }

    @Override
    public MultiDataSet next() {
        return next(batch());
    }

    public static INDArray getPhraseVector(Word2Vec word2Vec, String phrase, double freq) {
        INDArray vec = word2Vec.getLookupTable().vector(phrase);
        if(vec==null) return null;
        return vec.mul(freq);
    }
}
