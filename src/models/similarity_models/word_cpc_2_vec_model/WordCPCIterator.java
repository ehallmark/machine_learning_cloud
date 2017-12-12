package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import models.text_streaming.FileTextDataSetIterator;
import models.text_streaming.WordVectorizerAutoEncoderIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import seeding.Database;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WordCPCIterator implements SequenceIterator<VocabWord> {
    private static final Function<String,Map<String,Integer>> defaultBOWFunction = WordVectorizerAutoEncoderIterator.defaultBOWFunction;

    private static Random rand = new Random(56923);
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    private Function<Void,Void> afterEpochFunction;
    private FileTextDataSetIterator iterator;
    private Map<String,Collection<CPC>> cpcMap;
    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap) {
        this(iterator,numEpochs,cpcMap,null);
    }

    public WordCPCIterator(FileTextDataSetIterator iterator, int numEpochs, Map<String,Collection<CPC>> cpcMap, Function<Void,Void> afterEpochFunction) {
        this.numEpochs=numEpochs;
        this.iterator=iterator;
        this.cpcMap=cpcMap;
        this.queue = new ArrayBlockingQueue<>(100);
        this.vocabPass=true;
        this.afterEpochFunction=afterEpochFunction;
    }

    public void setRunVocab(boolean vocab) {
        this.vocabPass=vocab;
    }

    @Override
    public boolean hasMoreSequences() {
        if (task == null) {
            synchronized (this) {
                if (task == null) {
                    reset();
                }
            }
        }
        return queue.size()>0 || !task.isDone();
    }

    @Override
    public Sequence<VocabWord> nextSequence() {
        while (!task.isDone() && queue.isEmpty()) {
            try {
                TimeUnit.MILLISECONDS.sleep(5);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return queue.poll();
    }

    private static Sequence<VocabWord> extractSequenceFromDocumentAndTokens(LabelledDocument document, List<String> tokens, Random random) {
        if(document.getContent()==null||document.getLabels()==null||document.getContent().isEmpty() || document.getLabels().isEmpty()) return null;

        Map<String,Integer> wordCountMap = defaultBOWFunction.apply(document.getContent());

        List<String> contentWords = wordCountMap.entrySet().stream().filter(e->e.getValue()>0).flatMap(e-> IntStream.range(0,e.getValue()).mapToObj(i->e.getKey())).collect(Collectors.toList());

        List<VocabWord> words = new ArrayList<>(2*contentWords.size());

        for(int i = 0; i < contentWords.size(); i++) {
            VocabWord contentWord = new VocabWord(1,contentWords.get(random.nextInt(contentWords.size())));
            contentWord.setSequencesCount(1);
            contentWord.setElementFrequency(1);
            words.add(contentWord);
            if(tokens.size()>0) {
                VocabWord cpcWord = new VocabWord(1, tokens.get(random.nextInt(tokens.size())));
                words.add(cpcWord);
            }
        }

        Sequence<VocabWord> sequence = new Sequence<>(words);

        List<VocabWord> assigneeLabels = document.getLabels().stream()
                .map(asset-> Database.assigneeFor(asset))
                .filter(assignee->assignee!=null)
                .map(assignee->{
                    VocabWord label = new VocabWord(1,assignee);
                    label.setSpecial(true);
                    label.setSequencesCount(1);
                    return label;
                }).collect(Collectors.toList());
        sequence.setSequenceLabels(assigneeLabels);

        return sequence;

    }

    @Override
    public synchronized void reset() {
        if(task!=null && !task.isDone()) return;
        queue.clear();
        final boolean singleEpoch = vocabPass;
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                int finalNumEpochs = singleEpoch ? 1 : numEpochs;
                System.out.println("Running "+finalNumEpochs+" epochs: "+finalNumEpochs);
                for(int i = 0; i < finalNumEpochs; i++) {
                    while(iterator.hasNext()) {
                        LabelledDocument document = iterator.next();
                        if(document.getLabels()==null||document.getContent()==null) continue;

                        List<String> cpcs = document.getLabels().stream().flatMap(asset->cpcMap.getOrDefault(asset, Collections.emptyList()).stream()).map(cpc->cpc.getName()).collect(Collectors.toList());
                        if(cpcs.size()==0) continue;
                        // extract sequence
                        Sequence<VocabWord> sequence = extractSequenceFromDocumentAndTokens(document,cpcs,rand);
                        if(sequence==null) continue;
                        try {
                            queue.put(sequence);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    iterator.reset();
                    System.out.println("Finished epoch: "+(i+1));
                    // Evaluate model
                    if(afterEpochFunction!=null)afterEpochFunction.apply(null);
                }
            }
        };
        task.fork();
        vocabPass=false;
    }

}
