package dl4j_neural_nets.vectorization;

import dl4j_neural_nets.tools.LabeledCoOccurrences;
import lombok.NonNull;
import org.deeplearning4j.berkeley.Counter;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.learning.ElementsLearningAlgorithm;
import org.deeplearning4j.models.embeddings.loader.VectorsConfiguration;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.AdaGrad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class GloDV<T extends SequenceElement> implements ElementsLearningAlgorithm<T> {
    private VocabCache<T> vocabCache;
    private LabeledCoOccurrences<T> coOccurrences;
    private WeightLookupTable<T> lookupTable;
    private VectorsConfiguration configuration;
    private AtomicBoolean isTerminate = new AtomicBoolean(false);
    private INDArray syn0;
    private double xMax;
    private boolean shuffle;
    private boolean symmetric;
    protected double alpha = 0.75D;
    protected double learningRate = 0.0D;
    protected int maxmemory = 0;
    protected int batchSize = 1000;
    private AdaGrad weightAdaGrad;
    private AdaGrad biasAdaGrad;
    private INDArray bias;
    private int workers = Runtime.getRuntime().availableProcessors();
    private int vectorLength;
    private static final Logger log = LoggerFactory.getLogger(GloDV.class);

    public GloDV() {
    }

    public String getCodeName() {
        return "GloVe";
    }

    public void configure(@NonNull VocabCache<T> vocabCache, @NonNull WeightLookupTable<T> lookupTable, @NonNull VectorsConfiguration configuration) {
        if(vocabCache == null) {
            throw new NullPointerException("vocabCache");
        } else if(lookupTable == null) {
            throw new NullPointerException("lookupTable");
        } else if(configuration == null) {
            throw new NullPointerException("configuration");
        } else {
            this.vocabCache = vocabCache;
            this.lookupTable = lookupTable;
            this.configuration = configuration;
            this.syn0 = ((InMemoryLookupTable)lookupTable).getSyn0();
            this.vectorLength = configuration.getLayersSize();
            if(this.learningRate == 0.0D) {
                this.learningRate = configuration.getLearningRate();
            }

            this.weightAdaGrad = new AdaGrad(new int[]{this.vocabCache.numWords() + 1, this.vectorLength}, this.learningRate);
            this.bias = Nd4j.create(this.syn0.rows());
            this.biasAdaGrad = new AdaGrad(this.bias.shape(), this.learningRate);
            log.info("GloVe params: {Max Memory: [" + this.maxmemory + "], Learning rate: [" + this.learningRate + "], Alpha: [" + this.alpha + "], xMax: [" + this.xMax + "], Symmetric: [" + this.symmetric + "], Shuffle: [" + this.shuffle + "]}");
        }
    }

    public void pretrain(@NonNull SequenceIterator<T> iterator) {
        if(iterator == null) {
            throw new NullPointerException("iterator");
        } else {
            this.coOccurrences = (new LabeledCoOccurrences.Builder(xMax)).symmetric(this.symmetric).windowSize(this.configuration.getWindow()).iterate(iterator).workers(this.workers).vocabCache(this.vocabCache).maxMemory(this.maxmemory).build();
            this.coOccurrences.fit();
        }
    }

    public synchronized double learnSequence(@NonNull Sequence<T> sequence, @NonNull AtomicLong nextRandom, double learningRate) {
        if(sequence == null) {
            throw new NullPointerException("sequence");
        } else if(nextRandom == null) {
            throw new NullPointerException("nextRandom");
        } else if(this.isTerminate.get()) {
            return 0.0D;
        } else {
            AtomicLong pairsCount = new AtomicLong(0L);
            Counter<Integer> errorCounter = new Counter<>();

            for(int i = 0; i < this.configuration.getEpochs(); ++i) {
                Iterator pairs = this.coOccurrences.iterator();
                ArrayList threads = new ArrayList();

                int x;
                for(x = 0; x < this.workers; ++x) {
                    threads.add(x, new GloDVCalculationsThread(x, i, pairs, pairsCount, errorCounter));
                    ((GloDVCalculationsThread)threads.get(x)).start();
                }

                for(x = 0; x < this.workers; ++x) {
                    try {
                        ((GloDVCalculationsThread)threads.get(x)).join();
                    } catch (Exception var12) {
                        throw new RuntimeException(var12);
                    }
                }

                log.info("Processed [" + pairsCount.get() + "] pairs, Error was [" + errorCounter.getCount(i) + "]");
            }

            this.isTerminate.set(true);
            return 0.0D;
        }
    }

    public synchronized boolean isEarlyTerminationHit() {
        return this.isTerminate.get();
    }

    public void finish() {

    }

    private double iterateSample(T element1, T element2, double score) {
        if(element1.getIndex() >= 0 && element1.getIndex() < this.syn0.rows()) {
            if(element2.getIndex() >= 0 && element2.getIndex() < this.syn0.rows()) {
                INDArray w1Vector = this.syn0.slice(element1.getIndex());
                INDArray w2Vector = this.syn0.slice(element2.getIndex());
                double prediction = Nd4j.getBlasWrapper().dot(w1Vector, w2Vector);
                prediction += this.bias.getDouble(element1.getIndex()) + this.bias.getDouble(element2.getIndex()) - Math.log(score);
                double fDiff = score > this.xMax?prediction:Math.pow(score / this.xMax, this.alpha) * prediction;
                if(Double.isNaN(fDiff)) {
                    fDiff = Nd4j.EPS_THRESHOLD;
                }

                double gradient = fDiff * this.learningRate;
                this.update(element1, w1Vector, w2Vector, gradient);
                this.update(element2, w2Vector, w1Vector, gradient);
                return 0.5D * fDiff * prediction;
            } else {
                throw new IllegalArgumentException("Illegal index for word " + element2.getLabel());
            }
        } else {
            throw new IllegalArgumentException("Illegal index for word " + element1.getLabel());
        }
    }

    private void update(T element1, INDArray wordVector, INDArray contextVector, double gradient) {
        INDArray grad1 = contextVector.mul(Double.valueOf(gradient));
        INDArray update = this.weightAdaGrad.getGradient(grad1, element1.getIndex(), this.syn0.shape());
        wordVector.subi(update);
        double w1Bias = this.bias.getDouble(element1.getIndex());
        double biasGradient = this.biasAdaGrad.getGradient(gradient, element1.getIndex(), this.bias.shape());
        double update2 = w1Bias - biasGradient;
        this.bias.putScalar(element1.getIndex(), update2);
    }

    public static class Builder<T extends SequenceElement> {
        protected double xMax = 100.0D;
        protected double alpha = 0.75D;
        protected double learningRate = 0.0D;
        protected boolean shuffle = false;
        protected boolean symmetric = false;
        protected int maxmemory = 0;
        protected int batchSize = 1000;

        public Builder() {
        }

        public Builder<T> batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder<T> learningRate(double eta) {
            this.learningRate = eta;
            return this;
        }

        public Builder<T> alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        public Builder<T> maxMemory(int gbytes) {
            this.maxmemory = gbytes;
            return this;
        }

        public Builder<T> xMax(double xMax) {
            this.xMax = xMax;
            return this;
        }

        public Builder<T> shuffle(boolean reallyShuffle) {
            this.shuffle = reallyShuffle;
            return this;
        }

        public Builder<T> symmetric(boolean reallySymmetric) {
            this.symmetric = reallySymmetric;
            return this;
        }

        public GloDV<T> build() {
            GloDV ret = new GloDV();
            ret.symmetric = this.symmetric;
            ret.shuffle = this.shuffle;
            ret.xMax = this.xMax;
            ret.alpha = this.alpha;
            ret.learningRate = this.learningRate;
            ret.maxmemory = this.maxmemory;
            ret.batchSize = this.batchSize;
            return ret;
        }
    }

    private class GloDVCalculationsThread extends Thread implements Runnable {
        private final int threadId;
        private final int epochId;
        private final Iterator<Pair<Pair<T, T>, Double>> coList;
        private final AtomicLong pairsCounter;
        private final Counter<Integer> errorCounter;

        public GloDVCalculationsThread(int threadId, int epochId, @NonNull Iterator<Pair<Pair<T, T>, Double>> pairs, @NonNull AtomicLong pairsCounter, @NonNull Counter<Integer> errorCounter) {
            if(pairs == null) {
                throw new NullPointerException("coList");
            } else if(pairsCounter == null) {
                throw new NullPointerException("pairsCounter");
            } else if(errorCounter == null) {
                throw new NullPointerException("errorCounter");
            } else {
                this.epochId = epochId;
                this.threadId = threadId;
                this.pairsCounter = pairsCounter;
                this.errorCounter = errorCounter;
                this.coList = pairs;
                this.setName("GloVe ELA t." + this.threadId);
            }
        }

        public void run() {
            while(this.coList.hasNext()) {

                ArrayList pairs = new ArrayList();

                for(int cnt = 0; this.coList.hasNext() && cnt < GloDV.this.batchSize; ++cnt) {
                    pairs.add(this.coList.next());
                }
                if(GloDV.this.shuffle) {
                    Collections.shuffle(pairs);
                }

                Iterator iterator = pairs.iterator();


                int cnt = 0;
                while(iterator.hasNext() && cnt < GloDV.this.batchSize) {
                    Pair pairDoublePair = (Pair)iterator.next();
                    SequenceElement element1 = (SequenceElement)((Pair)pairDoublePair.getFirst()).getFirst();
                    SequenceElement element2 = (SequenceElement)((Pair)pairDoublePair.getFirst()).getSecond();
                    double weight = ((Double)pairDoublePair.getSecond()).doubleValue();
                    if(weight <= 0.0D) {
                        this.pairsCounter.incrementAndGet();
                    } else {
                        this.errorCounter.incrementCount(this.epochId, GloDV.this.iterateSample((T)element1, (T)element2, weight));
                        if(this.pairsCounter.incrementAndGet() % 1000000L == 0L) {
                            log.info("Processed [" + this.pairsCounter.get() + "] word pairs so far...");
                        }
                    }
                    cnt++;
                }
            }

        }
    }
}

