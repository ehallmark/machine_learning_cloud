package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WordCPCIterator implements SequenceIterator<VocabWord> {
    public static final Function<String,Map<String,Integer>> defaultBOWFunction = (content) -> {
        return Stream.of(content.split(",")).map(str->{
            String[] pair = str.split(":");
            if(pair.length==1) return null;
            return new Pair<>(pair[0],Integer.valueOf(pair[1]));
        }).filter(p->p!=null).collect(Collectors.toMap(p->p.getFirst(), p->p.getSecond()));
    };

    public static final Function<String,List<String>> defaultWordListFunction = (content) -> {
        return Arrays.asList(content.split("\\s+"));
    };

    private static Random rand = new Random(56923);
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    private Function<Void,Void> afterEpochFunction;
    private FileTextDataSetIterator iterator;
    private Map<String,Collection<CPC>> cpcMap;
    private int maxSamples;
    private int resetCounter = 0;
    private AtomicBoolean finished = new AtomicBoolean(true);
    private int minSequenceLength;
    private boolean fullText;
    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap, int minSequenceLength, int maxSamples, boolean fullText) {
        this(iterator,numEpochs,cpcMap,null,minSequenceLength,maxSamples,fullText);
    }

    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap, Function<Void,Void> afterEpochFunction, int minSequenceLength, int maxSamples, boolean fullText) {
        this.numEpochs=numEpochs;
        this.iterator=iterator;
        this.minSequenceLength=minSequenceLength;
        this.maxSamples=maxSamples;
        this.fullText=fullText;
        this.cpcMap=cpcMap;
        this.queue = new ArrayBlockingQueue<>(1000);
        this.vocabPass=true;
        this.afterEpochFunction=afterEpochFunction;
    }

    public void setRunVocab(boolean vocab) {
        this.vocabPass=vocab;
    }

    @Override
    public synchronized boolean hasMoreSequences() {
        return queue.size()>0 || !finished.get();
    }

    @Override
    public synchronized Sequence<VocabWord> nextSequence() {
        Sequence<VocabWord> sequence;
        while (true) {
            sequence = queue.poll();
            if(sequence!=null) break;
            if (task != null && task.isDone() && queue.isEmpty() && finished.get()) {
                System.out.println("TASK IS NOT NULL; TASK IS DONE; QUEUE IS EMPTY; BREAKING FROM WHILE LOOP NOW!!!!!!");
                break;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(2);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if(sequence==null) {
            System.out.println("SEQUENCE IS NULL!");
            return new Sequence<>(); // empty
        }
        return sequence;
    }

    private static Sequence<VocabWord> extractSequenceFromDocumentAndTokens(LabelledDocument document, List<String> tokens, Random random, int minSequenceLength, int maxSamples, boolean fullText) {
        if(document.getContent()==null||document.getLabels()==null||tokens.isEmpty()||document.getContent().isEmpty() || document.getLabels().isEmpty()) {
            //System.out.println("Returning NULL because content or labels are null");
            return null;
        }

        List<VocabWord> words;
        if(fullText) {
            List<String> text = defaultWordListFunction.apply(document.getContent());
            if(text.size()>=minSequenceLength) {
                int wordLimit = maxSamples > 0 ? Math.min(text.size(),maxSamples) : text.size();
                int start = text.size()>wordLimit ? random.nextInt(text.size()-wordLimit) : 0;
                words = text.stream().filter(word-> !Constants.STOP_WORD_SET.contains(word)).skip(start).limit(wordLimit).flatMap(word->{
                    VocabWord vocabWord = new VocabWord(1, word);
                    vocabWord.setSequencesCount(1);
                    vocabWord.setElementFrequency(1);
                    if(random.nextBoolean()) {
                        VocabWord cpc = new VocabWord(1, tokens.get(random.nextInt(tokens.size())));
                        cpc.setSequencesCount(1);
                        cpc.setElementFrequency(1);
                        if(random.nextBoolean()) {
                            return Stream.of(cpc,vocabWord);
                        } else {
                            return Stream.of(vocabWord,cpc);
                        }
                    }
                    return Stream.of(vocabWord);
                }).collect(Collectors.toList());

            } else {
                words = Collections.emptyList();
            }
        } else {
            Map<String, Integer> wordCountMap = defaultBOWFunction.apply(document.getContent());
            if (maxSamples <= 0) {
                words = new ArrayList<>();
                wordCountMap.forEach((word, cnt) -> {
                    VocabWord vocabWord = new VocabWord(cnt, word);
                    vocabWord.setSequencesCount(1);
                    vocabWord.setElementFrequency(cnt);
                    words.add(vocabWord);
                });
                int tokenCnt = wordCountMap.size() / tokens.size();
                tokens.forEach(token -> {
                    VocabWord vocabWord = new VocabWord(tokenCnt, token);
                    vocabWord.setSequencesCount(1);
                    vocabWord.setElementFrequency(tokenCnt);
                    words.add(vocabWord);
                });
            } else {
                String[] contentWords = new String[wordCountMap.size()];
                int[] contentCounts = new int[wordCountMap.size()];
                AtomicInteger total = new AtomicInteger(0);
                AtomicInteger idx = new AtomicInteger(0);
                wordCountMap.entrySet().forEach(e -> {
                    int i = idx.getAndIncrement();
                    int count = e.getValue();
                    contentCounts[i] = count;
                    contentWords[i] = e.getKey();
                    total.getAndAdd(count);
                });

                final int N = Math.min((total.get() + tokens.size()), maxSamples);

                if (N < minSequenceLength) {
                    //System.out.println("Returning NULL because N <= 0");
                    return null;
                }

                words = new ArrayList<>(N);

                for (int i = 0; i < N; i++) {
                    VocabWord word = null;
                    boolean randBool = rand.nextBoolean();
                    if (randBool || tokens.size() == 0) {
                        int r = rand.nextInt(total.get());
                        int s = 0;
                        for (int j = 0; j < contentCounts.length; j++) {
                            s += contentCounts[j];
                            if (s >= r) {
                                word = new VocabWord(1, contentWords[j]);
                                break;
                            }
                        }
                    }
                    if (!randBool || total.get() == 0) {
                        if (tokens.size() > 0) {
                            word = new VocabWord(1, tokens.get(random.nextInt(tokens.size())));
                        }
                    }
                    if (word != null) {
                        word.setSequencesCount(1);
                        word.setElementFrequency(1);
                        words.add(word);
                    }
                }
            }
        }

        if(words.isEmpty()) return null;

        Sequence<VocabWord> sequence = new Sequence<>(words);
        VocabWord label = new VocabWord(1,document.getLabels().get(0));
        label.setElementFrequency(1);
        label.setSequencesCount(1);
        label.setSpecial(true);
        sequence.setSequenceLabel(label);
        return sequence;
    }

    @Override
    public synchronized void reset() {
        resetCounter++;
        System.out.println("RESET CALLED: "+resetCounter);
        while(!finished.get()) {
            try {
                TimeUnit.MILLISECONDS.sleep(2l);
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
        finished.set(false);
        queue.clear();
        final int finalNumEpochs = vocabPass ? 1 : numEpochs;
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                System.out.println("Running "+finalNumEpochs+" epochs");
                try {
                    for (int i = 0; i < finalNumEpochs; i++) {
                        System.out.println("Starting epoch: " + (i + 1));
                        while (iterator.hasNext()) {
                            LabelledDocument document = iterator.next();
                            if (document.getLabels() == null || document.getContent() == null) continue;

                            List<String> cpcs = document.getLabels().stream().flatMap(asset -> cpcMap.getOrDefault(asset, Collections.emptyList()).stream()).flatMap(cpc -> IntStream.range(0,cpc.getNumParts()).mapToObj(c->cpc.getName())).collect(Collectors.toList());

                            // extract sequence
                            Sequence<VocabWord> sequence = extractSequenceFromDocumentAndTokens(document, cpcs, rand, minSequenceLength, maxSamples, fullText);
                            if (sequence != null) {
                                //System.out.print("-");
                                try {
                                    queue.put(sequence);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        System.out.println("Finished epoch: " + (i + 1));
                        // Evaluate model
                        if (afterEpochFunction != null) afterEpochFunction.apply(null);
                        iterator.reset();

                    }
                } catch(Exception e) {
                    e.printStackTrace();
                } finally {
                    finished.set(true);
                }
            }
        };
        task.fork();
        vocabPass=false;
    }

}
