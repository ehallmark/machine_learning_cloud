package models.similarity_models.keyword_embedding_model;

import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.stages.Stage;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.primitives.Pair;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public class FileSequenceIterator implements SequenceIterator<VocabWord> {
    private Set<String> onlyWords;
    private ArrayBlockingQueue<Sequence<VocabWord>> queue;
    private RecursiveAction task;
    private boolean vocabPass;
    private int numEpochs;
    public FileSequenceIterator(Set<String> onlyWords, int numEpochs) {
        this.onlyWords=onlyWords;
        this.numEpochs=numEpochs;
        this.queue = new ArrayBlockingQueue<>(1000);
        this.vocabPass=true;
    }


    @Override
    public boolean hasMoreSequences() {
        return queue.size()>0 || !task.isDone();
    }

    @Override
    public Sequence<VocabWord> nextSequence() {
        while(!task.isDone() && queue.isEmpty()) {
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            }catch(Exception e) {
                e.printStackTrace();
            }
        }
        return queue.poll();
    }

    @Override
    public void reset() {
        queue.clear();
        final boolean singleEpoch = vocabPass;
        Function<Pair<String,Map<MultiStem,Integer>>,Void> function = pair -> {
            Map<MultiStem,Integer> countMap = pair.getSecond();
            VocabWord label = new VocabWord(1,pair.getFirst());
            label.setSpecial(true);
            Collection<VocabWord> words = countMap.entrySet().stream()
                    .filter(word->onlyWords.contains(word.toString()))
                    .map(e->{
                        VocabWord word = new VocabWord(e.getValue(),e.getKey().toString());
                        return word;
                    }).collect(Collectors.toList());
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
                    }
                }
            }
        };
        task.fork();
        vocabPass=false;
    }
}
