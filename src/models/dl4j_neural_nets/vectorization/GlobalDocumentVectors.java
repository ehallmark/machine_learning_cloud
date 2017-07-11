package models.dl4j_neural_nets.vectorization;

import lombok.NonNull;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.loader.VectorsConfiguration;
import org.deeplearning4j.models.embeddings.reader.ModelUtils;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.glove.Glove;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.VectorsListener;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.text.documentiterator.DocumentIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;

import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 12/19/16.
 */
public class GlobalDocumentVectors extends Glove {
    protected GlobalDocumentVectors() {
    }


    @Override
    public void buildVocab() {
        VocabConstructor constructor = (new VocabConstructor.Builder()).addSource(this.iterator, this.minWordFrequency).setTargetVocabCache(this.vocab).fetchLabels(true).setStopWords(this.stopWords).build();
        if(this.existingModel != null && this.lookupTable instanceof InMemoryLookupTable && this.existingModel.lookupTable() instanceof InMemoryLookupTable) {
            log.info("Merging existing vocabulary into the current one...");
            constructor.buildMergedVocabulary(this.existingModel, true);
            ((InMemoryLookupTable)this.lookupTable).consume((InMemoryLookupTable)this.existingModel.lookupTable());
        } else {
            log.info("Starting vocabulary building...");
            constructor.buildJointVocabulary(false, true);
            if(this.useUnknown && this.unknownElement != null && !this.vocab.containsWord(this.unknownElement.getLabel())) {
                log.info("Adding UNK element...");
                this.unknownElement.setSpecial(true);
                this.unknownElement.markAsLabel(false);
                this.unknownElement.setIndex(this.vocab.numWords());
                this.vocab.addToken(this.unknownElement);
            }

            if((long)this.vocab.numWords() / constructor.getNumberOfSequences() > 1000L) {
                log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                log.warn("!                                                                                      !");
                log.warn("! Your input looks malformed: number of sentences is too low, model accuracy may suffer!");
                log.warn("!                                                                                      !");
                log.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
        }

    }


    public static class Builder extends Glove.Builder {
        private double xMax;
        private boolean shuffle;
        private boolean symmetric;
        protected double alpha = 0.75D;
        private int maxmemory = (int)(Runtime.getRuntime().totalMemory() / 1024L / 1024L / 1024L);
        protected TokenizerFactory tokenFactory;
        protected LabelAwareIterator labelAwareIterator;

        public Builder() {
        }

        public Builder(@NonNull VectorsConfiguration configuration) {
            super(configuration);
            if(configuration == null) {
                throw new NullPointerException("configuration");
            }
        }

        public Builder useExistingWordVectors(@NonNull WordVectors vec) {
            if(vec == null) {
                throw new NullPointerException("vec");
            } else {
                return this;
            }
        }

        public Builder iterate(@NonNull SequenceIterator<VocabWord> iterator) {
            if(iterator == null) {
                throw new NullPointerException("iterator");
            } else {
                super.iterate(iterator);
                return this;
            }
        }

        public Builder batchSize(int batchSize) {
            super.batchSize(batchSize);
            return this;
        }

        public Builder iterations(int iterations) {
            super.epochs(iterations);
            return this;
        }

        public Builder epochs(int numEpochs) {
            super.epochs(numEpochs);
            return this;
        }

        public Builder useAdaGrad(boolean reallyUse) {
            super.useAdaGrad(true);
            return this;
        }

        public Builder layerSize(int layerSize) {
            super.layerSize(layerSize);
            return this;
        }

        public Builder learningRate(double learningRate) {
            super.learningRate(learningRate);
            return this;
        }

        public Builder minWordFrequency(int minWordFrequency) {
            super.minWordFrequency(minWordFrequency);
            return this;
        }

        public Builder minLearningRate(double minLearningRate) {
            super.minLearningRate(minLearningRate);
            return this;
        }

        public Builder resetModel(boolean reallyReset) {
            super.resetModel(reallyReset);
            return this;
        }

        public Builder vocabCache(@NonNull VocabCache<VocabWord> vocabCache) {
            if(vocabCache == null) {
                throw new NullPointerException("vocabCache");
            } else {
                super.vocabCache(vocabCache);
                return this;
            }
        }

        public Builder lookupTable(@NonNull WeightLookupTable<VocabWord> lookupTable) {
            if(lookupTable == null) {
                throw new NullPointerException("lookupTable");
            } else {
                super.lookupTable(lookupTable);
                return this;
            }
        }

        /** @deprecated */
        @Deprecated
        public Builder sampling(double sampling) {
            super.sampling(sampling);
            return this;
        }

        /** @deprecated */
        @Deprecated
        public Builder negativeSample(double negative) {
            super.negativeSample(negative);
            return this;
        }

        public Builder stopWords(@NonNull List<String> stopList) {
            if(stopList == null) {
                throw new NullPointerException("stopList");
            } else {
                super.stopWords(stopList);
                return this;
            }
        }

        public Builder trainElementsRepresentation(boolean trainElements) {
            super.trainElementsRepresentation(true);
            return this;
        }

        /** @deprecated */
        @Deprecated
        public Builder trainSequencesRepresentation(boolean trainSequences) {
            super.trainSequencesRepresentation(true);
            return this;
        }

        public Builder stopWords(@NonNull Collection<VocabWord> stopList) {
            if(stopList == null) {
                throw new NullPointerException("stopList");
            } else {
                super.stopWords(stopList);
                return this;
            }
        }

        public Builder windowSize(int windowSize) {
            super.windowSize(windowSize);
            return this;
        }

        public Builder seed(long randomSeed) {
            super.seed(randomSeed);
            return this;
        }

        public Builder workers(int numWorkers) {
            super.workers(numWorkers);
            return this;
        }

        public Builder tokenizerFactory(@NonNull TokenizerFactory tokenizerFactory) {
            if(tokenizerFactory == null) {
                throw new NullPointerException("tokenizerFactory");
            } else {
                this.tokenFactory = tokenizerFactory;
                return this;
            }
        }

        public Builder xMax(double xMax) {
            this.xMax = xMax;
            return this;
        }

        public Builder symmetric(boolean reallySymmetric) {
            this.symmetric = reallySymmetric;
            return this;
        }

        public Builder shuffle(boolean reallyShuffle) {
            this.shuffle = reallyShuffle;
            return this;
        }

        public Builder alpha(double alpha) {
            this.alpha = alpha;
            return this;
        }

        public Builder iterate(@NonNull LabelAwareSentenceIterator iterator) {
            throw new UnsupportedOperationException("not yet implemented");
        }

        public Builder iterate(@NonNull DocumentIterator iterator) {
            throw new UnsupportedOperationException("not yet implemented");
        }

        public Builder modelUtils(@NonNull ModelUtils<VocabWord> modelUtils) {
            if(modelUtils == null) {
                throw new NullPointerException("modelUtils");
            } else {
                super.modelUtils(modelUtils);
                return this;
            }
        }

        public Builder setVectorsListeners(@NonNull Collection<VectorsListener<VocabWord>> vectorsListeners) {
            if(vectorsListeners == null) {
                throw new NullPointerException("vectorsListeners");
            } else {
                super.setVectorsListeners(vectorsListeners);
                return this;
            }
        }

        public Builder maxMemory(int gbytes) {
            this.maxmemory = gbytes;
            return this;
        }

        public Builder unknownElement(VocabWord element) {
            super.unknownElement(element);
            return this;
        }

        public Builder useUnknown(boolean reallyUse) {
            super.useUnknown(reallyUse);
            if(this.unknownElement == null) {
                this.unknownElement(new VocabWord(1.0D, "UNK"));
            }

            return this;
        }

        public GlobalDocumentVectors build() {
            this.presetTables();
            GlobalDocumentVectors ret = new GlobalDocumentVectors();
            if(this.labelAwareIterator != null) {
                SentenceTransformer transformer = (new SentenceTransformer.Builder()).iterator(this.labelAwareIterator).tokenizerFactory(this.tokenFactory).build();
                this.iterator = (new org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator.Builder(transformer)).build();
            }

            ret.trainElementsVectors = true;
            ret.trainSequenceVectors = false;
            ret.useAdeGrad = true;
            this.useAdaGrad = true;
            ret.learningRate.set(this.learningRate);
            ret.resetModel = this.resetModel;
            ret.batchSize = this.batchSize;
            ret.iterator = this.iterator;
            ret.numEpochs = this.numEpochs;
            ret.numIterations = this.iterations;
            ret.layerSize = this.layerSize;
            ret.useUnknown = this.useUnknown;
            ret.unknownElement = this.unknownElement;
            this.configuration.setLearningRate(this.learningRate);
            this.configuration.setLayersSize(this.layerSize);
            this.configuration.setHugeModelExpected(this.hugeModelExpected);
            this.configuration.setWindow(this.window);
            this.configuration.setMinWordFrequency(this.minWordFrequency);
            this.configuration.setIterations(this.iterations);
            this.configuration.setSeed(this.seed);
            this.configuration.setBatchSize(this.batchSize);
            this.configuration.setLearningRateDecayWords(this.learningRateDecayWords);
            this.configuration.setMinLearningRate(this.minLearningRate);
            this.configuration.setSampling(this.sampling);
            this.configuration.setUseAdaGrad(this.useAdaGrad);
            this.configuration.setNegative(this.negative);
            this.configuration.setEpochs(this.numEpochs);
            ret.configuration = this.configuration;
            ret.lookupTable = this.lookupTable;
            ret.vocab = this.vocabCache;
            ret.modelUtils = this.modelUtils;
            ret.eventListeners = this.vectorsListeners;
            ret.elementsLearningAlgorithm = (new GloDV.Builder<VocabWord>()).learningRate(this.learningRate).shuffle(this.shuffle).symmetric(this.symmetric).xMax(this.xMax).alpha(this.alpha).maxMemory(this.maxmemory).build();
            return ret;
        }
    }
}
