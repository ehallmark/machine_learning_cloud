package models.similarity_models.combined_similarity_model;

import models.similarity_models.Vectorizer;
import models.similarity_models.cpc_encoding_model.CPCSimilarityVectorizer;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 10/27/17.
 */
public class RecurrentWord2VecIterator implements DataSetIterator {
    private SequenceIterator<VocabWord> documentIterator;
    private Vectorizer vectorizer;
    private Word2Vec word2Vec;
    private int batch;
    private DataSet currentDataSet;
    private int numDimensions;
    private double numDocs;
    private int maxSamples;
    public RecurrentWord2VecIterator(SequenceIterator<VocabWord> documentIterator, long numDocs, Map<String, INDArray> cpcEncodings, Word2Vec word2Vec, int batchSize, int maxSamples) {
        this.documentIterator=documentIterator;
        this.vectorizer = new CPCSimilarityVectorizer(cpcEncodings,false,false,false);
        this.word2Vec=word2Vec;
        this.batch=batchSize;
        this.maxSamples=maxSamples;
        this.numDocs = numDocs;
        this.numDimensions=cpcEncodings.values().stream().findAny().get().length();
        reset();
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
        INDArray labels = Nd4j.create(batch,totalOutcomes());
        INDArray features = Nd4j.create(batch,inputColumns(),maxSamples);
        INDArray masks = Nd4j.create(batch,maxSamples);
        AtomicInteger wordsFoundPerBatch = new AtomicInteger(0);
        AtomicInteger totalWordsPerBatch = new AtomicInteger(0);
        while(documentIterator.hasMoreSequences()&&idx<batch) {
            Sequence<VocabWord> document = documentIterator.nextSequence();
            String label = document.getSequenceLabel().getLabel();
            INDArray labelVec = vectorizer.vectorFor(label);
            double[] mask = new double[maxSamples];
            if (labelVec == null) continue;
            labelVec = Transforms.unitVec(labelVec);
            List<VocabWord> vocabWords = document.getElements();
            if(vocabWords.isEmpty()) continue;
            totalWordsPerBatch.getAndAdd(vocabWords.size());
            List<INDArray> wordVectors = IntStream.range(0,maxSamples).mapToObj(i->{
                VocabWord vocabWord = vocabWords.size()>i?vocabWords.get(i):null;
                if(vocabWord==null) {
                    return Nd4j.zeros(totalOutcomes());
                }
                INDArray phraseVec = getPhraseVector(word2Vec, vocabWord.getLabel());
                if(phraseVec!=null) {
                    mask[i]=1d;
                    wordsFoundPerBatch.getAndIncrement();
                    return Transforms.unitVec(phraseVec);
                }
                return Nd4j.zeros(totalOutcomes());
            }).filter(vec->vec!=null).collect(Collectors.toList());
            if(wordVectors.isEmpty()) continue;
            INDArray featureVec = Nd4j.vstack(wordVectors).transpose();
            labels.putRow(idx,labelVec);
            masks.putRow(idx,Nd4j.create(mask));
            features.put(new INDArrayIndex[]{NDArrayIndex.point(idx),NDArrayIndex.all(),NDArrayIndex.all()},featureVec);
            idx++;
        }

        //System.out.println("Words found: "+wordsFoundPerBatch.get() + " / "+totalWordsPerBatch.get());

        if(idx>0) {
            if(idx < batch) {
                features = features.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all(),NDArrayIndex.all());
                labels = labels.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
                masks = masks.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
            }
            currentDataSet = new DataSet(features,labels,masks,masks);
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
