package dl4j_neural_nets.tools;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import dl4j_neural_nets.iterators.sequences.FilteredVocabSequenceIterator;
import lombok.NonNull;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.glove.count.ASCIICoOccurrenceWriter;
import org.deeplearning4j.models.glove.count.BinaryCoOccurrenceReader;
import org.deeplearning4j.models.glove.count.BinaryCoOccurrenceWriter;
import org.deeplearning4j.models.glove.count.CoOccurrenceWeight;
import org.deeplearning4j.models.glove.count.CoOccurrenceWriter;
import org.deeplearning4j.models.glove.count.CountMap;
import org.deeplearning4j.models.glove.count.RoundCount;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.SynchronizedSequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator;
import org.deeplearning4j.text.sentenceiterator.SynchronizedSentenceIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LabeledCoOccurrences<T extends SequenceElement> implements Serializable {
    protected boolean symmetric;
    protected int windowSize;
    protected VocabCache<T> vocabCache;
    protected SequenceIterator<T> sequenceIterator;
    protected int workers;
    protected File targetFile;
    protected ReentrantReadWriteLock lock;
    protected long memory_threshold;
    private ShadowCopyThread shadowThread;
    private volatile CountMap<T> coOccurrenceCounts;
    private AtomicLong processedSequences;
    private double xMax;
    protected static final Logger logger = LoggerFactory.getLogger(LabeledCoOccurrences.class);

    private LabeledCoOccurrences(double xMax) {
        this.workers = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        this.lock = new ReentrantReadWriteLock();
        this.memory_threshold = 0L;
        this.coOccurrenceCounts = new CountMap();
        this.processedSequences = new AtomicLong(0L);
        this.xMax=xMax;
    }

    public double getCoOccurrenceCount(@NonNull T element1, @NonNull T element2) {
        if(element1 == null) {
            throw new NullPointerException("element1");
        } else if(element2 == null) {
            throw new NullPointerException("element2");
        } else {
            return this.coOccurrenceCounts.getCount(element1, element2);
        }
    }

    protected long getMemoryFootprint() {
        long var1;
        try {
            this.lock.readLock().lock();
            var1 = (long)this.coOccurrenceCounts.size() * 24L * 5L;
        } finally {
            this.lock.readLock().unlock();
        }

        return var1;
    }

    protected long getMemoryThreshold() {
        return this.memory_threshold / 2L;
    }

    public void fit() {
        this.shadowThread = new ShadowCopyThread();
        this.shadowThread.start();
        this.sequenceIterator.reset();
        ArrayList threads = new ArrayList();

        int x;
        for(x = 0; x < this.workers; ++x) {
            threads.add(x, new CoOccurrencesCalculatorThread(x, new FilteredVocabSequenceIterator(new SynchronizedSequenceIterator(this.sequenceIterator), this.vocabCache), this.processedSequences));
            ((CoOccurrencesCalculatorThread)threads.get(x)).start();
        }

        for(x = 0; x < this.workers; ++x) {
            try {
                ((CoOccurrencesCalculatorThread)threads.get(x)).join();
            } catch (Exception var4) {
                throw new RuntimeException(var4);
            }
        }

        this.shadowThread.finish();
        logger.info("CoOccurrences map was built.");
    }

    public Iterator<Pair<Pair<T, T>, Double>> iterator() {
        final SynchronizedSentenceIterator iterator;
        try {
            iterator = new SynchronizedSentenceIterator((new org.deeplearning4j.text.sentenceiterator.PrefetchingSentenceIterator.Builder(new BasicLineIterator(this.targetFile))).setFetchSize(500000).build());
        } catch (Exception var3) {
            logger.error("Target file was not found on last stage!");
            throw new RuntimeException(var3);
        }

        return new Iterator() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public Pair<Pair<T, T>, Double> next() {
                String line = iterator.nextSentence();
                String[] strings = line.split(" ");
                int idx0 = Integer.valueOf(strings[0]);
                int idx1 = Integer.valueOf(strings[1]);
                if(idx0 >= 0 && idx1 >= 0) {
                    T element1 = LabeledCoOccurrences.this.vocabCache.elementAtIndex(idx0);
                    T element2 = LabeledCoOccurrences.this.vocabCache.elementAtIndex(idx1);
                    Double weight = Double.valueOf(strings[2]);
                    return new Pair(new Pair(element1, element2), weight);
                } else {
                    return next();
                }
            }

            public void remove() {
                throw new UnsupportedOperationException("remove() method can\'t be supported on read-only interface");
            }
        };
    }

    private class ShadowCopyThread extends Thread implements Runnable {
        private AtomicBoolean isFinished = new AtomicBoolean(false);
        private AtomicBoolean isTerminate = new AtomicBoolean(false);
        private AtomicBoolean isInvoked = new AtomicBoolean(false);
        private AtomicBoolean shouldInvoke = new AtomicBoolean(false);
        private File[] tempFiles;
        private RoundCount counter;

        public ShadowCopyThread() {
            try {
                this.counter = new RoundCount(1);
                this.tempFiles = new File[2];
                this.tempFiles[0] = File.createTempFile("aco", "tmp");
                this.tempFiles[1] = File.createTempFile("aco", "tmp");
                this.tempFiles[0].deleteOnExit();
                this.tempFiles[1].deleteOnExit();
            } catch (Exception var3) {
                throw new RuntimeException(var3);
            }

            this.setName("ACO ShadowCopy thread");
        }

        public void run() {
            while(!this.isFinished.get() && !this.isTerminate.get()) {
                if(LabeledCoOccurrences.this.getMemoryFootprint() > LabeledCoOccurrences.this.getMemoryThreshold() || this.shouldInvoke.get() && !this.isInvoked.get()) {
                    this.shouldInvoke.compareAndSet(true, false);
                    this.invokeBlocking();
                } else {
                    try {
                        Thread.sleep(1000L);
                    } catch (Exception var2) {
                        throw new RuntimeException(var2);
                    }
                }
            }

        }

        public void invoke() {
            this.shouldInvoke.compareAndSet(false, true);
        }

        public synchronized void invokeBlocking() {
            if(LabeledCoOccurrences.this.getMemoryFootprint() >= LabeledCoOccurrences.this.getMemoryThreshold() || this.isFinished.get()) {
                int numberOfLinesSaved = 0;
                this.isInvoked.set(true);
                LabeledCoOccurrences.logger.debug("Memory purge started.");
                this.counter.tick();

                CountMap localMap;
                try {
                    LabeledCoOccurrences.this.lock.writeLock().lock();
                    localMap = LabeledCoOccurrences.this.coOccurrenceCounts;
                    LabeledCoOccurrences.this.coOccurrenceCounts = new CountMap();
                } catch (Exception var15) {
                    throw new RuntimeException(var15);
                } finally {
                    LabeledCoOccurrences.this.lock.writeLock().unlock();
                }

                try {
                    File e = null;
                    if(!this.isFinished.get()) {
                        e = this.tempFiles[this.counter.previous()];
                    } else {
                        e = LabeledCoOccurrences.this.targetFile;
                    }

                    int linesRead = 0;
                    LabeledCoOccurrences.logger.debug("Saving to: [" + this.counter.get() + "], Reading from: [" + this.counter.previous() + "]");
                    BinaryCoOccurrenceReader reader = new BinaryCoOccurrenceReader(this.tempFiles[this.counter.previous()], LabeledCoOccurrences.this.vocabCache, localMap);
                    Object writer = this.isFinished.get()?new ASCIICoOccurrenceWriter(LabeledCoOccurrences.this.targetFile):new BinaryCoOccurrenceWriter(this.tempFiles[this.counter.get()]);

                    while(reader.hasMoreObjects()) {
                        CoOccurrenceWeight iterator = reader.nextObject();
                        if(iterator != null) {
                            ((CoOccurrenceWriter)writer).writeObject(iterator);
                            ++numberOfLinesSaved;
                            ++linesRead;
                        }
                    }

                    reader.finish();
                    LabeledCoOccurrences.logger.debug("Lines read: [" + linesRead + "]");
                    Iterator var18 = localMap.getPairIterator();

                    while(true) {
                        if(!var18.hasNext()) {
                            ((CoOccurrenceWriter)writer).finish();
                            localMap = null;
                            break;
                        }

                        Pair pair = (Pair)var18.next();
                        double mWeight = localMap.getCount(pair);
                        CoOccurrenceWeight object = new CoOccurrenceWeight();
                        object.setElement1((SequenceElement)pair.getFirst());
                        object.setElement2((SequenceElement)pair.getSecond());
                        object.setWeight(mWeight);
                        ((CoOccurrenceWriter)writer).writeObject(object);
                        ++numberOfLinesSaved;
                    }
                } catch (Exception var17) {
                    throw new RuntimeException(var17);
                }

                LabeledCoOccurrences.logger.info("Number of word pairs saved so far: [" + numberOfLinesSaved + "]");
                this.isInvoked.set(false);
            }
        }

        public void finish() {
            if(!this.isFinished.get()) {
                this.isFinished.set(true);
                this.invokeBlocking();
            }
        }

        public void terminate() {
            this.isTerminate.set(true);
        }
    }

    private class CoOccurrencesCalculatorThread extends Thread implements Runnable {
        private final SequenceIterator<T> iterator;
        private final AtomicLong sequenceCounter;
        private int threadId;

        public CoOccurrencesCalculatorThread(int threadId, @NonNull SequenceIterator<T> iterator, @NonNull AtomicLong sequenceCounter) {
            if(iterator == null) {
                throw new NullPointerException("iterator");
            } else if(sequenceCounter == null) {
                throw new NullPointerException("sequenceCounter");
            } else {
                this.iterator = iterator;
                this.sequenceCounter = sequenceCounter;
                this.threadId = threadId;
                this.setName("CoOccurrencesCalculatorThread " + threadId);
            }
        }

        public void run() {
            while(this.iterator.hasMoreSequences()) {
                Sequence<T> sequence = this.iterator.nextSequence();
                List<String> tokens = new ArrayList<>(sequence.asLabels());
                List<T> labels = sequence.getSequenceLabels();

                for(int x = 0; x < sequence.getElements().size(); ++x) {
                    int wordIdx = LabeledCoOccurrences.this.vocabCache.indexOf(tokens.get(x));
                    if(wordIdx >= 0) {
                        int windowStop = Math.min(x + LabeledCoOccurrences.this.windowSize + 1, tokens.size());
                        T tokenX = LabeledCoOccurrences.this.vocabCache.wordFor(tokens.get(x));
                        for(int i = 0; i < labels.size(); i++) {
                            T label = labels.get(i);
                            LabeledCoOccurrences.this.coOccurrenceCounts.incrementCount(label, (T)tokenX, 1.0);
                            //if(LabeledCoOccurrences.this.symmetric) {
                            LabeledCoOccurrences.this.coOccurrenceCounts.incrementCount((T)tokenX, label, 1.0);
                        }
                        for(int j = x; j < windowStop; ++j) {
                            int otherWord = LabeledCoOccurrences.this.vocabCache.indexOf((String)tokens.get(j));
                            if(otherWord >= 0) {
                                String w2 = LabeledCoOccurrences.this.vocabCache.wordFor((String)tokens.get(j)).getLabel();
                                if(!w2.equals("UNK") && otherWord != wordIdx) {
                                    SequenceElement tokenJ = LabeledCoOccurrences.this.vocabCache.wordFor((String)tokens.get(j));
                                    double nWeight = 1.0D / ((double)(j - x) + Nd4j.EPS_THRESHOLD);

                                    while(LabeledCoOccurrences.this.getMemoryFootprint() >= LabeledCoOccurrences.this.getMemoryThreshold()) {
                                        try {
                                            LabeledCoOccurrences.this.shadowThread.invoke();
                                            if(this.threadId == 0) {
                                                LabeledCoOccurrences.logger.debug("Memory consuimption > threshold: {footrpint: [" + LabeledCoOccurrences.this.getMemoryFootprint() + "], threshold: [" + LabeledCoOccurrences.this.getMemoryThreshold() + "] }");
                                            }

                                            Thread.sleep(10000L);
                                        } catch (Exception var23) {
                                            throw new RuntimeException(var23);
                                        } finally {

                                        }
                                    }

                                    try {
                                        LabeledCoOccurrences.this.lock.readLock().lock();
                                        if(wordIdx < otherWord) {
                                            LabeledCoOccurrences.this.coOccurrenceCounts.incrementCount((T)tokenX, (T)tokenJ, nWeight);
                                            if(LabeledCoOccurrences.this.symmetric) {
                                                LabeledCoOccurrences.this.coOccurrenceCounts.incrementCount((T)tokenJ, (T)tokenX, nWeight);
                                            }
                                        } else {
                                            LabeledCoOccurrences.this.coOccurrenceCounts.incrementCount((T)tokenJ, (T)tokenX, nWeight);
                                            if(LabeledCoOccurrences.this.symmetric) {
                                                LabeledCoOccurrences.this.coOccurrenceCounts.incrementCount((T)tokenX, (T)tokenJ, nWeight);
                                            }
                                        }

                                    } finally {
                                        LabeledCoOccurrences.this.lock.readLock().unlock();
                                    }
                                }
                            }
                        }
                    }
                }

                this.sequenceCounter.incrementAndGet();
            }

        }
    }

    public static class Builder<T extends SequenceElement> {
        protected boolean symmetric;
        protected int windowSize = 5;
        protected VocabCache<T> vocabCache;
        protected SequenceIterator<T> sequenceIterator;
        protected int workers = Runtime.getRuntime().availableProcessors();
        protected File target;
        protected long maxmemory = Runtime.getRuntime().maxMemory();
        private double xMax;

        public Builder(double xMax) {
            this.xMax=xMax;
        }

        public Builder<T> symmetric(boolean reallySymmetric) {
            this.symmetric = reallySymmetric;
            return this;
        }

        public Builder<T> windowSize(int windowSize) {
            this.windowSize = windowSize;
            return this;
        }

        public Builder<T> vocabCache(@NonNull VocabCache<T> cache) {
            if(cache == null) {
                throw new NullPointerException("cache");
            } else {
                this.vocabCache = cache;
                return this;
            }
        }

        public Builder<T> iterate(@NonNull SequenceIterator<T> iterator) {
            if(iterator == null) {
                throw new NullPointerException("iterator");
            } else {
                this.sequenceIterator = new SynchronizedSequenceIterator(iterator);
                return this;
            }
        }

        public Builder<T> workers(int numWorkers) {
            this.workers = numWorkers;
            return this;
        }

        public Builder<T> maxMemory(int gbytes) {
            if(gbytes > 0) {
                this.maxmemory = (long)(Math.max(gbytes - 1, 1) * 1024 * 1024) * 1024L;
            }

            return this;
        }

        public Builder<T> targetFile(@NonNull String path) {
            if(path == null) {
                throw new NullPointerException("path");
            } else {
                this.targetFile(new File(path));
                return this;
            }
        }

        public Builder<T> targetFile(@NonNull File file) {
            if(file == null) {
                throw new NullPointerException("file");
            } else {
                this.target = file;
                return this;
            }
        }

        public LabeledCoOccurrences<T> build() {
            LabeledCoOccurrences ret = new LabeledCoOccurrences(xMax);
            ret.sequenceIterator = this.sequenceIterator;
            ret.windowSize = this.windowSize;
            ret.vocabCache = this.vocabCache;
            ret.symmetric = this.symmetric;
            ret.workers = this.workers;
            if(this.maxmemory < 1L) {
                this.maxmemory = Runtime.getRuntime().maxMemory();
            }

            ret.memory_threshold = this.maxmemory;
            LabeledCoOccurrences.logger.info("Actual memory limit: [" + this.maxmemory + "]");

            try {
                if(this.target == null) {
                    this.target = File.createTempFile("cooccurrence", "map");
                }

                this.target.deleteOnExit();
            } catch (Exception var3) {
                throw new RuntimeException(var3);
            }

            ret.targetFile = this.target;
            return ret;
        }
    }
}
