package dl4j_neural_nets.iterators.sequences;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;
import org.deeplearning4j.models.word2vec.VocabWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Evan on 1/15/2017.
 */
public class AsyncSequenceIterator implements SequenceIterator<VocabWord> {
    private SequenceIterator<VocabWord> iterator;
    private LinkedList<Sequence<VocabWord>> queue = new LinkedList<>();
    private List<RecursiveAction> threads = new ArrayList<>();
    private int numThreads;
    private final int seekDistance = 50;
    private AtomicBoolean noMoreSequences = new AtomicBoolean(false);
    public AsyncSequenceIterator(SequenceIterator<VocabWord> iterator, int numThreads) {
        this.iterator=iterator;
        this.numThreads=numThreads;
        startThreads();
    }

    @Override
    public Sequence<VocabWord> nextSequence() {
        synchronized (queue) {
            return queue.removeFirst();
        }
    }

    private void startThreads() {
        for(int n = threads.size(); n < numThreads; n++) {
            RecursiveAction thread = new RecursiveAction() {
                @Override
                protected void compute() {
                    int counter = 0;
                    while (counter < seekDistance) {
                        Sequence<VocabWord> sequence;
                        synchronized (iterator) {
                            boolean hasMoreSequences = iterator.hasMoreSequences();
                            if(!hasMoreSequences) {
                                noMoreSequences.set(true);
                                break;
                            }
                            sequence=iterator.nextSequence();
                        }
                        synchronized (queue) {
                            queue.add(sequence);
                        }
                        counter++;
                    }
                }
            };
            thread.fork();
            threads.add(thread);
        }
    }
    @Override
    public void reset() {
        noMoreSequences.set(false);
        threads.clear();
        startThreads();
        iterator.reset();
        queue.clear();
    }

    @Override
    public boolean hasMoreSequences() {
        if(!queue.isEmpty()) return true;
        else {
            if(noMoreSequences.get()) {
                threads.forEach(thread->thread.join());
                threads.clear();
            }
            while(!threads.isEmpty()) {
                threads.remove(0).join();
                startThreads();
                if(!queue.isEmpty()) {
                    return true;
                }
            }
        }
        return !queue.isEmpty();
    }


}
