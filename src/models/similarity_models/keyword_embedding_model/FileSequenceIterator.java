package models.similarity_models.keyword_embedding_model;

import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.stages.Stage;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.primitives.Pair;

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
public class FileSequenceIterator implements SequenceIterator<VocabWord> {
    private Set<String> onlyWords;
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    private Function<Void,Void> afterEpochFunction;
    public FileSequenceIterator(Set<String> onlyWords, int numEpochs) {
        this(onlyWords,numEpochs,null);
    }

    public FileSequenceIterator(Set<String> onlyWords, int numEpochs, Function<Void,Void> afterEpochFunction) {
        this.onlyWords=onlyWords;
        this.numEpochs=numEpochs;
        this.queue = new ArrayBlockingQueue<>(5000);
        this.vocabPass=true;
        this.afterEpochFunction=afterEpochFunction;
    }

    public void setRunVocab(boolean vocab) {
        this.vocabPass=vocab;
    }


    @Override
    public boolean hasMoreSequences() {
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

    @Override
    public void reset() {
        if(task!=null) task.join();
        queue.clear();
        final boolean singleEpoch = vocabPass;
        Function<Pair<String,Map<MultiStem,Integer>>,Void> function = pair -> {
            Map<MultiStem,Integer> countMap = pair.getSecond();
            VocabWord label = new VocabWord();
            label.setElementFrequency(1);
            label.setSequencesCount(1);
            label.setWord(pair.getFirst());
            label.setSpecial(true);
            List<VocabWord> words = countMap.entrySet().stream()
                    .filter(e->onlyWords.contains(e.getKey().toString()))
                    .flatMap(e->{
                        VocabWord word = new VocabWord();
                        word.setSequencesCount(1);
                        word.setElementFrequency(1);
                        word.setWord(e.getKey().toString());
                        return IntStream.range(0,e.getValue()).mapToObj(i->word);
                    }).collect(Collectors.toList());
            Collections.shuffle(words);
            Sequence<VocabWord> sequence = new Sequence<>(words);
            sequence.setSequenceLabel(label);
            try {
                queue.put(sequence);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };
        task = new RecursiveAction() {
            @Override
            protected void compute() {
                if(singleEpoch) {
                    System.out.println("Running single epoch");
                    Stage.runSamplingIteratorWithLabels(function);
                } else {
                    System.out.println("Running multiple epochs: "+numEpochs);
                    for(int i = 0; i < numEpochs; i++) {
                        Stage.runSamplingIteratorWithLabels(function);
                        System.out.println("Finished epoch: "+(i+1));
                        // Evaluate model
                        if(afterEpochFunction!=null)afterEpochFunction.apply(null);
                    }
                }
            }
        };
        task.fork();
        vocabPass=false;
    }
}
