package models.dl4j_neural_nets.iterators.datasets;

import models.dl4j_neural_nets.tools.MyTokenizerFactory;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/8/16.
 */
public class InMemoryDataSetConstructor implements DataSetIterator {
    private double subSampling = -1.0;
    private SequenceIterator<VocabWord> sequenceIterator;
    private Word2Vec model;
    private boolean randomizeData = true;
    private int maxSequenceLength = 10000;
    private int batchSize = 10;
    private int numInputs;
    private int numOutputs;
    private int numSamples = 10;
    private List<DataSet> dataSets;
    private List<String> labels;
    private Iterator<DataSet> iterator;
    private Map<String,Pair<Float,INDArray>> vocabMap;
    private long totalWordCount;

    private InMemoryDataSetConstructor(SequenceIterator<VocabWord> sequenceIterator, Word2Vec model, int numInputs, int numOutputs, List<String> labels) {
        this.sequenceIterator=sequenceIterator;
        this.model=model;
        this.numInputs = numInputs;
        this.numOutputs = numOutputs;
        this.labels=labels;
        this.dataSets=new ArrayList<>();
        totalWordCount = model.getVocab().totalWordOccurrences();
    }

    private InMemoryDataSetConstructor(SequenceIterator<VocabWord> sequenceIterator, Map<String,Pair<Float,INDArray>> model, int numInputs, int numOutputs, List<String> labels) {
        this.sequenceIterator=sequenceIterator;
        this.vocabMap=model;
        this.numInputs = numInputs;
        this.numOutputs = numOutputs;
        this.labels=labels;
        this.dataSets=new ArrayList<>();
        totalWordCount=model.values().stream().collect(Collectors.summingLong(pair->pair.getFirst().longValue()));
    }

    private InMemoryDataSetConstructor(LabelAwareIterator labeledIterator, Word2Vec model, int numInputs, int numOutputs, List<String> labels) {
        this(new AbstractSequenceIterator.Builder<>(new SentenceTransformer.Builder().tokenizerFactory(new MyTokenizerFactory()).iterator(labeledIterator).build()).build(),model,numInputs,numOutputs,labels);
    }

    private InMemoryDataSetConstructor(LabelAwareSentenceIterator labeledIterator, Word2Vec model, int numInputs, int numOutputs, List<String> labels) {
        this(new AbstractSequenceIterator.Builder<>(new SentenceTransformer.Builder().tokenizerFactory(new MyTokenizerFactory()).iterator(labeledIterator).build()).build(),model,numInputs,numOutputs,labels);
    }

    private void init() {
        int numPasses = 1;
        if(subSampling > 0.0) numPasses = numSamples;
        for(int pass = 0; pass < numPasses; pass++) {
            sequenceIterator.reset();
            while (sequenceIterator.hasMoreSequences()) {
                INDArray inputs = Nd4j.zeros(batchSize, numInputs);
                INDArray outputs = Nd4j.zeros(batchSize, numOutputs);
                for (int i = 0; i < batchSize; i++) {
                    Sequence<VocabWord> sequence = sequenceIterator.nextSequence();
                    Random rand = new Random(System.currentTimeMillis());
                    List<VocabWord> words = sequence.getElements().stream()
                            .filter(word -> {
                                if(model!=null) {
                                    if (word == null || !model.getVocab().containsWord(word.getWord())) return false;
                                    if (subSampling > 0.0) {
                                        double wordCounts = model.getVocab().wordFor(word.getWord()).getElementFrequency();
                                        assert wordCounts >= 1.0 : "Bad word counts!";
                                        double freq = new Double(wordCounts) / totalWordCount;
                                        if (rand.nextDouble() > Math.sqrt(subSampling / freq)) return false;
                                    }
                                } else {
                                    if (word == null || !vocabMap.containsKey(word.getWord())) return false;
                                    if (subSampling > 0.0) {
                                        double wordCounts = model.getVocab().wordFor(word.getWord()).getElementFrequency();
                                        assert wordCounts >= 1.0 : "Bad word counts!";
                                        double freq = new Double(wordCounts) / totalWordCount;
                                        if (rand.nextDouble() > Math.sqrt(subSampling / freq)) return false;
                                    }
                                }
                                return true;
                            }).limit(maxSequenceLength).collect(Collectors.toList());
                    if (words.isEmpty()) {
                        i--;
                    } else {
                        INDArray inputRow = inputs.getRow(i);
                        INDArray outputRow = outputs.getRow(i);
                        double total = 0.0;
                        for (int j = 0; j < words.size(); j++) {
                            INDArray wordVector;
                            if(model==null) {
                                Pair<Float,INDArray> pair = vocabMap.get(words.get(j).getWord());
                                wordVector=pair.getSecond();
                            } else {
                                VocabWord word = words.get(j);
                                wordVector=model.getWordVectorMatrix(word.getWord());
                            }
                            inputRow.addi(wordVector);
                        }
                        inputRow.divi(words.size());

                        List<VocabWord> sequenceLabels = sequence.getSequenceLabels();
                        sequenceLabels.forEach(label -> {
                            assert labels.contains(label);
                            outputRow.putScalar(labels.indexOf(label.getLabel()), 1.0);
                        });
                        if(labels.size()>1)outputRow.divi(labels.size());
                    }
                    if (!sequenceIterator.hasMoreSequences()) return;
                }
                dataSets.add(new DataSet(inputs, outputs));
            }
            sequenceIterator = null;
            System.gc();
            System.gc();
            System.gc();
            System.gc();
        }
    }

    @Override
    public DataSet next(int i) {
        return next();
    }

    @Override
    public int totalExamples() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int inputColumns() {
        return numInputs;
    }

    @Override
    public int totalOutcomes() {
        return numOutputs;
    }

    public boolean resetSupported() {
        return true;
    }

    public boolean asyncSupported() {
        return true;
    }

    @Override
    public void reset() {
        if(randomizeData)Collections.shuffle(dataSets,new Random(System.currentTimeMillis()));
        iterator=dataSets.iterator();
    }

    @Override
    public int batch() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int cursor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int numExamples() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor dataSetPreProcessor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getLabels() {
        return labels;
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public DataSet next() {
        return iterator.next();
    }


    public static class Builder {
        private InMemoryDataSetConstructor constructor;
        public Builder(SequenceIterator<VocabWord> sequenceIterator,Word2Vec model, int numInputs, int numOutputs, List<String> labels) {
            this.constructor=new InMemoryDataSetConstructor(sequenceIterator, model, numInputs, numOutputs,labels);
        }
        public Builder(SequenceIterator<VocabWord> sequenceIterator,Map<String,Pair<Float,INDArray>> model, int numInputs, int numOutputs, List<String> labels) {
            this.constructor=new InMemoryDataSetConstructor(sequenceIterator, model, numInputs, numOutputs,labels);
        }
        public Builder(LabelAwareIterator iterator,Word2Vec model, int numInputs, int numOutputs, List<String> labels) {
            this.constructor=new InMemoryDataSetConstructor(iterator, model, numInputs, numOutputs,labels);
        }
        public Builder(LabelAwareSentenceIterator iterator, Word2Vec model, int numInputs, int numOutputs, List<String> labels) {
            this.constructor=new InMemoryDataSetConstructor(iterator, model, numInputs, numOutputs,labels);
        }
        public Builder setSubSampling(double sampling) {
            constructor.subSampling=sampling;
            return this;
        }
        public Builder setBatchSize(int batchSize) {
            constructor.batchSize=batchSize;
            return this;
        }
        public Builder setNumSamples(int samples) {
            constructor.numSamples=samples;
            return this;
        }
        public Builder setMaxSequenceLength(int length) {
            constructor.maxSequenceLength=length;
            return this;
        }
        public Builder randomizeDataEachEpoch(boolean reallyRandomize) {
            constructor.randomizeData=reallyRandomize;
            return this;
        }
        public DataSetIterator build() {
            constructor.init();
            constructor.reset();
            return constructor;
        }
    }
}
