package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap, int maxSamples) {
        this(iterator,numEpochs,cpcMap,null,maxSamples);
    }

    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap, Function<Void,Void> afterEpochFunction, int maxSamples) {
        this.numEpochs=numEpochs;
        this.iterator=iterator;
        this.maxSamples=maxSamples;
        this.cpcMap=cpcMap;
        this.queue = new ArrayBlockingQueue<>(500);
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
            if (task != null && task.isDone() && queue.isEmpty()) {
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
        }
        return sequence;
    }

    private static Sequence<VocabWord> extractSequenceFromDocumentAndTokens(LabelledDocument document, List<String> tokens, Random random, int maxSamples) {
        if(document.getContent()==null||document.getLabels()==null||document.getContent().isEmpty() || document.getLabels().isEmpty()) {
            //System.out.println("Returning NULL because content or labels are null");
            return null;
        }

        Map<String,Integer> wordCountMap = defaultBOWFunction.apply(document.getContent());

        String[] contentWords = new String[wordCountMap.size()];
        int[] contentCounts = new int[wordCountMap.size()];
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger idx = new AtomicInteger(0);
        wordCountMap.entrySet().forEach(e->{
            int i = idx.getAndIncrement();
            int count = e.getValue();
            contentCounts[i]=count;
            contentWords[i]=e.getKey();
            total.getAndAdd(count);
        });

        final int N = Math.min((total.get()+tokens.size()),maxSamples);

        if(N<=0) {
            //System.out.println("Returning NULL because N <= 0");
            return null;
        }

        String assignee = document.getLabels().stream()
                .map(asset-> Database.assigneeFor(asset))
                .filter(a->a!=null)
                .findFirst().orElse(null);

        List<VocabWord> words = new ArrayList<>(N);
        for(int i = 0; i < N; i++) {

            VocabWord word = null;
            int randInt = rand.nextInt(tokens.size()+total.get());
            if(randInt < total.get()) {
                int r = rand.nextInt(total.get());
                int s = 0;
                for(int j = 0; j < contentCounts.length; j++) {
                    s += contentCounts[j];
                    if(s >= r) {
                        word = new VocabWord(1,contentWords[j]);
                        break;
                    }
                }
            } else {
                if(tokens.size()>0) {
                    word = new VocabWord(1, tokens.get(random.nextInt(tokens.size())));
                }
            }
            if(word!=null) {
                word.setSequencesCount(1);
                word.setElementFrequency(1);
                words.add(word);
            }
        }

        Sequence<VocabWord> sequence = new Sequence<>(words);
        if(assignee!=null) {
            VocabWord label = new VocabWord(1, assignee);
            label.setSpecial(true);
            label.setElementFrequency(1);
            label.setSequencesCount(1);
            sequence.setSequenceLabel(label);
        }

        return sequence;
    }

    @Override
    public synchronized void reset() {
        resetCounter++;
        System.out.println("RESET CALLED: "+resetCounter);
        if(!finished.get()) return;
        finished.set(false);
        queue.clear();
        final int finalNumEpochs = vocabPass ? 1 : numEpochs;
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                System.out.println("Running "+finalNumEpochs+" epochs");
                for(int i = 0; i < finalNumEpochs; i++) {
                    while(iterator.hasNext()) {
                        LabelledDocument document = iterator.next();
                        if(document.getLabels()==null||document.getContent()==null) continue;

                        List<String> cpcs = document.getLabels().stream().flatMap(asset->cpcMap.getOrDefault(asset, Collections.emptyList()).stream()).map(cpc->cpc.getName()).collect(Collectors.toList());

                        // extract sequence
                        Sequence<VocabWord> sequence = extractSequenceFromDocumentAndTokens(document,cpcs,rand,maxSamples);
                        if(sequence!=null) {
                            //System.out.print("-");
                            try {
                                queue.put(sequence);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    iterator.reset();
                    System.out.println("Finished epoch: "+(i+1));
                    // Evaluate model
                    if(afterEpochFunction!=null)afterEpochFunction.apply(null);

                    finished.set(true);
                }
            }
        };
        task.fork();
        vocabPass=false;
    }

}
