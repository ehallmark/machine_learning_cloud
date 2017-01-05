package dl4j_neural_nets.iterators.sequences;

import lombok.NonNull;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by ehallmark on 12/20/16.
 */
public class FilteredVocabSequenceIterator<T extends SequenceElement> implements SequenceIterator<T> {
    private final SequenceIterator<T> underlyingIterator;
    private final VocabCache<T> vocabCache;

    public FilteredVocabSequenceIterator(@NonNull SequenceIterator<T> iterator, @NonNull VocabCache<T> vocabCache) {
        if(iterator == null) {
            throw new NullPointerException("iterator");
        } else if(vocabCache == null) {
            throw new NullPointerException("vocabCache");
        } else {
            this.vocabCache = vocabCache;
            this.underlyingIterator = iterator;
        }
    }

    public boolean hasMoreSequences() {
        return this.underlyingIterator.hasMoreSequences();
    }

    public Sequence<T> nextSequence() {
        Sequence<T> originalSequence = this.underlyingIterator.nextSequence();
        Sequence<T> newSequence = new Sequence<>();
        if(originalSequence != null) {
            Iterator<T> var3 = originalSequence.getElements().iterator();

            while(var3.hasNext()) {
                T element = var3.next();
                if(element != null && this.vocabCache.containsWord(element.getLabel())) {
                    newSequence.addElement(this.vocabCache.elementAtIndex(vocabCache.indexOf(element.getLabel())));
                }
            }
            // labels
            if(originalSequence.getSequenceLabels()!=null) {
                List<T> filteredLabels = new ArrayList<>(originalSequence.getSequenceLabels().size());
                Iterator<T> labelIterator = originalSequence.getSequenceLabels().iterator();
                while(labelIterator.hasNext()) {
                    T label = labelIterator.next();
                    if(label != null) {
                        int labelIdx = this.vocabCache.indexOf(label.getLabel());
                        if(labelIdx >= 0) {
                            filteredLabels.add(vocabCache.elementAtIndex(labelIdx));
                        }
                    }
                }
                newSequence.setSequenceLabels(filteredLabels);
            }
        }
        newSequence.setSequenceId(originalSequence.getSequenceId());
        return newSequence;
    }

    public void reset() {
        this.underlyingIterator.reset();
    }
}
