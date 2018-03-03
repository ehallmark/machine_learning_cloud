package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import lombok.Getter;
import lombok.Setter;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    public static final Function<String,String[]> defaultWordListFunction = (content) -> {
        return content.split(" ");
    };

    private static final Random rand = new Random();
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    private FileTextDataSetIterator iterator;
    private Map<String,Collection<CPC>> cpcMap;
    private int maxSamples;
    @Getter @Setter
    private int vocabSampling = 0;
    private int resetCounter = 0;
    private AtomicBoolean finished = new AtomicBoolean(true);
    private boolean fullText;
    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap, int maxSamples, boolean fullText) {
        this.numEpochs=numEpochs;
        this.iterator=iterator;
        this.maxSamples=maxSamples;
        this.fullText=fullText;
        this.cpcMap=cpcMap;
        this.queue = new ArrayBlockingQueue<>(1000);
        this.vocabPass=true;
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

    private static Sequence<VocabWord> extractSequenceFromDocumentAndTokens(LabelledDocument document, List<String> tokens, Random random, int maxSamples, boolean fullText) {
        if(document.getContent()==null||document.getLabels()==null||document.getContent().isEmpty() || document.getLabels().isEmpty()) {
            //System.out.println("Returning NULL because content or labels are null");
            return null;
        }

        List<VocabWord> words;
        if(fullText) {
            String[] text = defaultWordListFunction.apply(document.getContent());
            int wordLimit = maxSamples > 0 ? Math.min(text.length,maxSamples) : text.length;
            int start = text.length>wordLimit&&maxSamples>0 ? random.nextInt(text.length-wordLimit) : 0;
            final double cpcProb = random.nextDouble();

            words = IntStream.range(start,Math.min(text.length,start+wordLimit)).mapToObj(i->{
                String word;
                if(rand.nextDouble()<cpcProb||tokens.size()==0) {
                    word = text[i];
                } else {
                    word = tokens.get(random.nextInt(tokens.size()));
                }
                VocabWord vocabWord = new VocabWord(1, word);
                vocabWord.setSequencesCount(1);
                vocabWord.setElementFrequency(1);
                return vocabWord;
            }).collect(Collectors.toList());

        } else {
            Map<String, Integer> wordCountMap = defaultBOWFunction.apply(document.getContent());
            if(wordCountMap.size()==0) return null;
            words = new ArrayList<>();
            wordCountMap.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(maxSamples).forEach(e -> {
                VocabWord vocabWord = new VocabWord(e.getValue(), e.getKey());
                vocabWord.setSequencesCount(1);
                vocabWord.setElementFrequency(e.getValue());
                words.add(vocabWord);
            });
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
        final int finalNumSamples = vocabPass ? vocabSampling : maxSamples;
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                System.out.println("Running "+finalNumEpochs+" epochs");
                try {
                    for (int i = 0; i < finalNumEpochs; i++) {
                        System.out.println("Starting epoch: " + (i + 1));
                        while (iterator.hasNext()) {
                            LabelledDocument document = iterator.next();
                            LocalDate date = iterator.getCurrentDate();
                            if (document.getLabels() == null || document.getContent() == null) continue;

                            List<String> cpcs = document.getLabels().stream().flatMap(asset -> cpcMap.getOrDefault(asset, Collections.emptyList()).stream())
                                    .filter(cpc->fullText || cpc.getNumParts()>=3).flatMap(cpc -> fullText ? IntStream.range(0,cpc.getNumParts()).mapToObj(c->cpc.getName()) : Stream.of(cpc.getName())).collect(Collectors.toCollection(ArrayList::new));

                            // extract sequence
                            Sequence<VocabWord> sequence = extractSequenceFromDocumentAndTokens(document, cpcs, rand, finalNumSamples, fullText);
                            if (date!=null&&sequence != null) {
                                sequence.addSequenceLabel(new VocabWord(1,date.toString()));
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
