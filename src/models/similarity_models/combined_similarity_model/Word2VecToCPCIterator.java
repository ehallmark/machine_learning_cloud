package models.similarity_models.combined_similarity_model;

import lombok.Getter;
import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.text_streaming.WordVectorizerToCPCVectorIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import tools.Stemmer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/27/17.
 */
public class Word2VecToCPCIterator implements DataSetIterator {
    private static Function<List<VocabWord>,Map<String,Integer>> defaultBOWFunction = sequence -> {
        return sequence.stream().collect(Collectors.toMap(word->word.getLabel(),word->(int)word.getElementFrequency()));
    };

    private SequenceIterator<VocabWord> documentIterator;
    private Vectorizer vectorizer;
    private Word2Vec word2Vec;
    private int batch;
    private DataSet currentDataSet;
    private int numDimensions;
    public Word2VecToCPCIterator(SequenceIterator<VocabWord> documentIterator, Map<String, INDArray> cpcEncodings, Word2Vec word2Vec, int batchSize) {
        this.documentIterator=documentIterator;
        this.vectorizer = new CPCSimilarityVectorizer(cpcEncodings,false,false,false);
        this.word2Vec=word2Vec;
        this.batch=batchSize;
        this.numDimensions=cpcEncodings.values().stream().findAny().get().length();
    }

    @Override
    public DataSet next(int batch) {
        return currentDataSet;
    }

    @Override
    public int totalExamples() {
        return -1;
    }

    @Override
    public int inputColumns() {
        return word2Vec.getLayerSize();
    }

    @Override
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
        return 0;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {

    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public List<String> getLabels() {
        throw new UnsupportedOperationException("labels()");
    }

    @Override
    public boolean hasNext() {
        currentDataSet=null;
        int idx = 0;
        List<INDArray> labels = new ArrayList<>();
        List<INDArray> features = new ArrayList<>();
        AtomicInteger wordsFoundPerBatch = new AtomicInteger(0);
        AtomicInteger totalWordsPerBatch = new AtomicInteger(0);
        while(documentIterator.hasMoreSequences()&&idx<batch) {
            Sequence<VocabWord> document = documentIterator.nextSequence();
            String label = document.getSequenceLabel().getLabel();
            INDArray labelVec = vectorizer.vectorFor(label);
            if (labelVec == null) continue;
            AtomicInteger found = new AtomicInteger(0);
            Map<String, Integer> wordCounts = defaultBOWFunction.apply(document.getElements());
            totalWordsPerBatch.getAndAdd(wordCounts.size());
            wordCounts.entrySet().forEach(e -> {
                INDArray phraseVec = getPhraseVector(word2Vec, e.getKey());
                if(phraseVec!=null) {
                    wordsFoundPerBatch.getAndIncrement();
                    found.getAndIncrement();
                    for (int i = 0; i < e.getValue(); i++) {
                        features.add(phraseVec);
                        labels.add(labelVec);
                    }
                }
            });
            if(found.get()==0) continue;
            idx++;
        }
        int seed = 10;
        Collections.shuffle(labels, new Random(seed));
        Collections.shuffle(features, new Random(seed));

        System.out.println("Words found: "+wordsFoundPerBatch.get() + " / "+totalWordsPerBatch.get());

        if(idx>0) {
            currentDataSet = new DataSet(Nd4j.vstack(features),Nd4j.vstack(labels));
        }
        return currentDataSet!=null;
    }

    @Override
    public DataSet next() {
        return next(batch());
    }

    public static INDArray getPhraseVector(Word2Vec word2Vec, String phrase) {
        return word2Vec.getLookupTable().vector(phrase);
    }
}
