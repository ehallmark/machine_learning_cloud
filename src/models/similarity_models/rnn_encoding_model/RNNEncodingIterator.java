package models.similarity_models.rnn_encoding_model;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.List;
import java.util.stream.Collectors;

public class RNNEncodingIterator implements MultiDataSetIterator{
    private Word2Vec word2Vec;
    private SequenceIterator<VocabWord> iterator;
    private MultiDataSetPreProcessor preProcessor;
    private int batchSize;
    private int maxSequenceLength;
    public RNNEncodingIterator(Word2Vec word2Vec, SequenceIterator<VocabWord> iterator, int batchSize, int maxSequenceLength) {
        this.word2Vec=word2Vec;
        this.maxSequenceLength=maxSequenceLength;
        this.iterator=iterator;
        this.batchSize=batchSize;
    }

    @Override
    public MultiDataSet next(int n) {
        Sequence<VocabWord> sequence = iterator.nextSequence();
        if(sequence==null) return null;
        INDArray allFeatures = Nd4j.zeros(n,word2Vec.getLayerSize(),maxSequenceLength*2);
        INDArray allLabels = Nd4j.zeros(n,word2Vec.getLayerSize(),maxSequenceLength*2);
        INDArray featureMasks = Nd4j.zeros(n,maxSequenceLength*2);
        INDArray labelMasks = Nd4j.zeros(n,maxSequenceLength*2);

        int i = 0;
        while(i < n && iterator.hasMoreSequences()) {
            List<String> validWords = sequence.getElements().stream()
                    .filter(w -> word2Vec.hasWord(w.getLabel()))
                    .map(w -> w.getLabel())
                    .limit(maxSequenceLength)
                    .collect(Collectors.toList());

            INDArray wordVectors = word2Vec.getWordVectors(validWords).transpose();
            allFeatures.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.interval(0,validWords.size())).assign(wordVectors);
            featureMasks.get(NDArrayIndex.point(i),NDArrayIndex.interval(0,validWords.size())).assign(1f);
            allLabels.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.interval(validWords.size(),validWords.size()*2)).assign(wordVectors);
            labelMasks.get(NDArrayIndex.point(i),NDArrayIndex.interval(validWords.size(),validWords.size()*2)).assign(1f);
            i++;
        }

        if(i < n) {
            allFeatures = allFeatures.get(NDArrayIndex.interval(0,i),NDArrayIndex.all(),NDArrayIndex.all());
            allLabels = allLabels.get(NDArrayIndex.interval(0,i),NDArrayIndex.all(),NDArrayIndex.all());
            featureMasks = featureMasks.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
            labelMasks = labelMasks.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
        }
        MultiDataSet dataSet = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{allFeatures},new INDArray[]{allLabels}, new INDArray[]{featureMasks}, new INDArray[]{labelMasks});
        if(this.preProcessor!=null) {
            this.preProcessor.preProcess(dataSet);
        }
        return dataSet;
    }

    @Override
    public void setPreProcessor(MultiDataSetPreProcessor preProcessor) {
        this.preProcessor=preProcessor;
    }

    @Override
    public MultiDataSetPreProcessor getPreProcessor() {
        return preProcessor;
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
        iterator.reset();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasMoreSequences();
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }
}
