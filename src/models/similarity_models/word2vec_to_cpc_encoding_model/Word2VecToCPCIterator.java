package models.similarity_models.word2vec_to_cpc_encoding_model;

import lombok.Getter;
import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.text_streaming.WordVectorizerToCPCVectorIterator;
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
    private static Function<String,Map<String,Integer>> defaultBOWFunction = content -> {
        return Stream.of(content.split(",")).map(str->{
            String[] pair = str.split(":");
            if(pair.length==1) return null;
            return new Pair<>(pair[0],Integer.valueOf(pair[1]));
        }).filter(p->p!=null).collect(Collectors.toMap(p->p.getFirst(), p->p.getSecond()));
    };

    @Getter
    private Set<String> onlyWords;
    private LabelAwareIterator documentIterator;
    private Vectorizer vectorizer;
    private Word2Vec word2Vec;
    private int batch;
    private DataSet currentDataSet;
    private int numDimensions;
    public Word2VecToCPCIterator(LabelAwareIterator documentIterator, Map<String, INDArray> cpcEncodings, Set<String> onlyWords, Word2Vec word2Vec, int batchSize, boolean binarize, boolean normalize, boolean probability) {
        this.onlyWords=onlyWords;
        this.documentIterator=documentIterator;
        this.vectorizer = new CPCSimilarityVectorizer(cpcEncodings,binarize,normalize,probability);
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
        return word2Vec.getLayerSize() * 3;
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
        while(documentIterator.hasNext()&&idx<batch) {
            System.out.println("Starting batch: "+idx);
            LabelledDocument document = documentIterator.next();
            String label = document.getLabels().get(0);
            INDArray labelVec = vectorizer.vectorFor(label);
            if (labelVec == null) continue;
            AtomicInteger found = new AtomicInteger(0);
            Map<String, Integer> wordCounts = defaultBOWFunction.apply(document.getContent());
            wordCounts.entrySet().stream().filter(e->onlyWords.contains(e.getKey())).forEach(e -> {
                INDArray phraseVec = getPhraseVector(word2Vec, e.getKey());
                if(phraseVec!=null) {
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
        String[] words = phrase.split("\\s+");
        System.out.println("Phrase: "+phrase);
        List<INDArray> validWords = Stream.of(words).map(word->word2Vec.getLookupTable().vector(word)).filter(vec->vec!=null).collect(Collectors.toList());
        if(validWords.isEmpty()) return null;
        if(validWords.size()==1) {
            return Nd4j.hstack(validWords.get(0),validWords.get(0),validWords.get(0));
        } else if(validWords.size()==2) {
            return Nd4j.hstack(validWords.get(0),validWords.get(0).add(validWords.get(1)).divi(2d),validWords.get(1));
        } else {
            return Nd4j.hstack(validWords.get(0),validWords.get(1),validWords.get(2));
        }
    }
}
