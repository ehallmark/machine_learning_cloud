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
import java.util.Set;
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
    private Set<String> cpc2VecSet;
    public RecurrentWord2VecIterator(SequenceIterator<VocabWord> documentIterator, long numDocs, Map<String, INDArray> cpcEncodings, Word2Vec word2Vec, int batchSize, int maxSamples, Set<String> cpc2VecSet) {
        this.documentIterator=documentIterator;
        this.vectorizer = new CPCSimilarityVectorizer(cpcEncodings,false,false,false);
        this.word2Vec=word2Vec;
        this.batch=batchSize;
        this.cpc2VecSet=cpc2VecSet;
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
        INDArray featureMasks = Nd4j.create(batch,maxSamples);
        INDArray labelMasks = Nd4j.create(batch,maxSamples);
        AtomicInteger wordsFoundPerBatch = new AtomicInteger(0);
        AtomicInteger totalWordsPerBatch = new AtomicInteger(0);
        while(documentIterator.hasMoreSequences()&&idx<batch) {
            Sequence<VocabWord> document = documentIterator.nextSequence();
            String label = document.getSequenceLabel().getLabel();
            INDArray labelVec = vectorizer.vectorFor(label);
            double[] featureMask = new double[maxSamples];
            double[] labelMask = new double[maxSamples];
            if (labelVec == null) continue;
            labelVec = Transforms.unitVec(labelVec);
            List<VocabWord> vocabWords = document.getElements().stream().filter(word->!cpc2VecSet.contains(word.getLabel())).collect(Collectors.toList());
            if(vocabWords.isEmpty()) continue;
            totalWordsPerBatch.getAndAdd(vocabWords.size());
            AtomicInteger lastIdx = new AtomicInteger(-1);
            INDArray featureVec = Nd4j.create(inputColumns(),maxSamples);
            IntStream.range(0,maxSamples).forEach(i->{
                VocabWord vocabWord = vocabWords.size()>i?vocabWords.get(i):null;
                INDArray phraseVec = null;
                if(vocabWord!=null) {
                    phraseVec = getPhraseVector(word2Vec, vocabWord.getLabel());
                    if (phraseVec != null) {
                        featureMask[i] = 1d;
                        lastIdx.set(i);
                        wordsFoundPerBatch.getAndIncrement();
                        phraseVec = Transforms.unitVec(phraseVec);
                    }
                }
                if(phraseVec==null) {
                    phraseVec = Nd4j.zeros(inputColumns());
                }
                featureVec.putColumn(i,phraseVec);
            });
            if(lastIdx.get()<0) continue;
            labelMask[lastIdx.get()]=1d;
            labels.putRow(idx,labelVec);
            featureMasks.putRow(idx,Nd4j.create(featureMask));
            labelMasks.putRow(idx,Nd4j.create(labelMask));
            features.put(new INDArrayIndex[]{NDArrayIndex.point(idx),NDArrayIndex.all(),NDArrayIndex.all()},featureVec);
            idx++;
        }

        //System.out.println("Words found: "+wordsFoundPerBatch.get() + " / "+totalWordsPerBatch.get());

        if(idx>0) {
            if(idx < batch) {
                features = features.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all(),NDArrayIndex.all());
                labels = labels.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
                featureMasks = featureMasks.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
                labelMasks = labelMasks.get(NDArrayIndex.interval(0,idx),NDArrayIndex.all());
            }
            currentDataSet = new DataSet(features,labels,featureMasks,labelMasks);
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
