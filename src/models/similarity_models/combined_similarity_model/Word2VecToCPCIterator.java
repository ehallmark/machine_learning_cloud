package models.similarity_models.combined_similarity_model;

import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/27/17.
 */
public class Word2VecToCPCIterator implements MultiDataSetIterator {
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
    public Word2VecToCPCIterator(SequenceIterator<VocabWord> documentIterator, long numDocs, Map<String, INDArray> cpcEncodings, Word2Vec word2Vec, int batchSize, boolean requireLabel) {
        this.documentIterator=documentIterator;
        this.requireLabel=requireLabel;
        this.vectorizer = cpcEncodings==null?null: new CPCSimilarityVectorizer(cpcEncodings,false,false,false);
        this.word2Vec=word2Vec;
        this.batch=batchSize;
        this.numDocs = numDocs;
        this.numDimensions=cpcEncodings.values().stream().findAny().get().length();
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
        INDArray labels = requireLabel?Nd4j.create(batch,totalOutcomes()):null;
        INDArray features = Nd4j.create(batch,inputColumns());
        INDArray dates = Nd4j.create(batch,1);
        AtomicInteger wordsFoundPerBatch = new AtomicInteger(0);
        AtomicInteger totalWordsPerBatch = new AtomicInteger(0);
        LocalDate today = LocalDate.now();
        while(documentIterator.hasMoreSequences()&&idx<batch) {
            Sequence<VocabWord> document = documentIterator.nextSequence();
            String label = document.getSequenceLabel().getLabel();
            if(document.getSequenceLabels().size()<2) continue;
            LocalDate date = LocalDate.parse(document.getSequenceLabels().get(1).getLabel(), DateTimeFormatter.ISO_DATE);
            INDArray labelVec = labels==null||vectorizer==null?null:vectorizer.vectorFor(label);
            if (labelVec == null && requireLabel) continue;
            Map<String, Integer> wordCounts = groupingBOWFunction.apply(document.getElements());
            totalWordsPerBatch.getAndAdd(wordCounts.size());
            List<INDArray> wordVectors = wordCounts.entrySet().stream().map(e -> {
                double tf = e.getValue();
                double idf = Math.log(1d+(numDocs/Math.max(30,word2Vec.getVocab().docAppearedIn(e.getKey()))));
                INDArray phraseVec = getPhraseVector(word2Vec, e.getKey(),tf*idf);
                if(phraseVec!=null) {
                    wordsFoundPerBatch.getAndIncrement();
                    return phraseVec;
                }
                return null;
            }).filter(vec->vec!=null).collect(Collectors.toList());
            if(wordVectors.isEmpty()) continue;
            INDArray featureVec = Nd4j.vstack(wordVectors).mean(0);
            if(labels!=null)labels.putRow(idx,labelVec);
            double remainingLife = (((double)today.getYear()+((double)today.getMonthValue()-1)/12) - ((double)date.getYear()+((double)date.getMonthValue()-1)/12));
            double remainingLifeNorm = (remainingLife-10d)/10d;
            dates.putScalar(idx,0, remainingLifeNorm);
            features.putRow(idx,featureVec);
            idx++;
        }

        //System.out.println("Words found: "+wordsFoundPerBatch.get() + " / "+totalWordsPerBatch.get());

        if(idx>0) {
            if(idx < batch) {
                features = features.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
                if(labels!=null)labels = labels.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
                dates = dates.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
            }
            features.diviColumnVector(features.norm2(1));
            if(labels!=null)labels.diviColumnVector(labels.norm2(1));
            INDArray[] allFeatures = labels!=null ? new INDArray[]{features,labels,dates} : new INDArray[]{features,dates};
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
