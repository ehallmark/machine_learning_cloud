package models.similarity_models.rnn_word2vec_to_cpc_vae_model;

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
import java.util.stream.Stream;

public class RnnToVaeIterator implements MultiDataSetIterator{
    private Word2Vec word2Vec;
    private PostgresVectorizedSequenceIterator iterator;
    private MultiDataSetPreProcessor preProcessor;
    private int batchSize;
    private int maxSequenceLength;
    public RnnToVaeIterator(Word2Vec word2Vec, PostgresVectorizedSequenceIterator iterator, int batchSize, int maxSequenceLength) {
        this.word2Vec=word2Vec;
        this.maxSequenceLength=maxSequenceLength;
        this.iterator=iterator;
        this.batchSize=batchSize;
    }

    @Override
    public MultiDataSet next(int n) {
        INDArray allFeatures = Nd4j.zeros(n,word2Vec.getLayerSize(),maxSequenceLength);
        INDArray allLabels = Nd4j.zeros(n,word2Vec.getLayerSize());
        INDArray featureMasks = Nd4j.zeros(n,maxSequenceLength);

        int i = 0;
        while(i < n && iterator.hasMoreSequences()) {
            Sequence<VocabWord> sequence = iterator.nextSequence();
            if(sequence==null||sequence.isEmpty()) continue;
            List<String> validWords = sequence.getElements().stream()
                    .filter(w -> word2Vec.hasWord(w.getLabel()))
                    .map(w -> w.getLabel())
                    .limit(maxSequenceLength)
                    .collect(Collectors.toList());

            INDArray wordVectors = word2Vec.getWordVectors(validWords).transpose();
            INDArray label = Nd4j.create(Stream.of(iterator.getVector()).mapToDouble(v->v.doubleValue()).toArray());
            allFeatures.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.interval(0,validWords.size())).assign(wordVectors);
            featureMasks.get(NDArrayIndex.point(i),NDArrayIndex.interval(0,validWords.size())).assign(1f);
            allLabels.get(NDArrayIndex.point(i),NDArrayIndex.all()).assign(label);
            i++;
        }

        if(i < n) {
            allFeatures = allFeatures.get(NDArrayIndex.interval(0,i),NDArrayIndex.all(),NDArrayIndex.all());
            allLabels = allLabels.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
            featureMasks = featureMasks.get(NDArrayIndex.interval(0,i),NDArrayIndex.all());
        }
        MultiDataSet dataSet = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{allFeatures},new INDArray[]{allLabels}, new INDArray[]{featureMasks}, null);
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
