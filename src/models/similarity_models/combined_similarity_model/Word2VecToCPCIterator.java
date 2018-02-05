package models.similarity_models.combined_similarity_model;

import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import org.apache.commons.lang.ArrayUtils;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/27/17.
 */
public class Word2VecToCPCIterator implements MultiDataSetIterator {
    private static Random rand = new Random(23);
    public static final Function<List<VocabWord>,Map<String,Integer>> groupingBOWFunction = sequence -> {
        return sequence.stream().collect(Collectors.groupingBy(word->word.getLabel(), Collectors.summingInt(label->(int)label.getElementFrequency())));
    };
    private static Function<List<VocabWord>,Map<String,Integer>> countBOWFunction = sequence -> {
        return sequence.stream().collect(Collectors.toMap(word->word.getLabel(),word->(int)word.getElementFrequency()));
    };


    private SequenceIterator<VocabWord> documentIterator;
    private Vectorizer vectorizer;
    private Word2Vec word2Vec;
    private int batch;
    private MultiDataSet currentDataSet;
    private int numDimensions;
    private double numDocs;
    private boolean requireLabel;
    public Word2VecToCPCIterator(SequenceIterator<VocabWord> documentIterator, long numDocs, Map<String, INDArray> cpcEncodings, Word2Vec word2Vec, int batchSize, boolean requireLabel, int numOutcomes) {
        this.documentIterator=documentIterator;
        this.requireLabel=requireLabel;
        this.vectorizer = cpcEncodings==null?null: new CPCSimilarityVectorizer(cpcEncodings,false,false,false);
        this.word2Vec=word2Vec;
        this.batch=batchSize;
        this.numDocs = numDocs;
        this.numDimensions=numOutcomes;
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
        return numDimensions;
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
        INDArray features = Nd4j.create(batch,inputColumns());
        AtomicInteger totalWordsPerBatch = new AtomicInteger(0);
        while(documentIterator.hasMoreSequences()&&idx<batch) {
            Sequence<VocabWord> document = documentIterator.nextSequence();

            Map<String, Integer> wordCounts = groupingBOWFunction.apply(document.getElements());
            totalWordsPerBatch.getAndAdd(wordCounts.size());

            List<String> sequence = wordCounts.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(1).map(e->e.getKey()).collect(Collectors.toList());
            if(sequence.size()==0) continue;
            INDArray featureVec;
            {
                List<Integer> indexes = new ArrayList<>();
                List<Double> tfidfs = new ArrayList<>();
                for (int i = 0; i < sequence.size(); i++) {
                    String l = sequence.get(i);
                    if (word2Vec.getVocab().containsWord(l)) {
                        indexes.add(word2Vec.getVocab().indexOf(l));
                        tfidfs.add(Math.max(1d, wordCounts.get(l).doubleValue()) * Math.log(Math.E + (numDocs / Math.max(word2Vec.getVocab().docAppearedIn(l), 30))));
                    }
                }


                if (indexes.size() == 0) {
                    System.out.print("no indices...");
                    continue;
                }

                featureVec = word2Vec.getLookupTable().getWeights().getRow(indexes.get(0));

            }
            features.putRow(idx,featureVec);
            idx++;
        }

        if(idx>0) {
            if(idx < batch) {
                features = features.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
            }
            features.diviColumnVector(features.norm2(1));
            INDArray[] allFeatures = new INDArray[]{features};
            currentDataSet = new MultiDataSet(allFeatures,allFeatures);
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
