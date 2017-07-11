package models.dl4j_neural_nets.tools;

import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.sequence.SequenceElement;

import java.io.Serializable;
import java.util.Collection;

/**
 * Created by Evan on 1/14/2017.
 */
public class DuplicatableSequence<T extends SequenceElement> extends Sequence<T> implements Serializable {
    public DuplicatableSequence(Collection<T> words) {
        super(words);
    }

    public DuplicatableSequence() {
        super();
    }

    public Sequence<T> dup() {
        DuplicatableSequence<T> seq = new DuplicatableSequence<>();
        seq.elements = this.elements;
        seq.elementsMap = this.elementsMap;
        seq.hash = 0;
        seq.hashCached = false;
        return seq;
    }
}
