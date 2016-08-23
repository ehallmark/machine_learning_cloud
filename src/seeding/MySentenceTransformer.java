package seeding;

/**
 * Created by ehallmark on 8/22/16.
 */

import lombok.NonNull;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.sequencevectors.transformers.SequenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.text.documentiterator.BasicLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.DocumentIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelledDocument;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.Tokenizer;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This simple class is responsible for conversion lines of text to Sequences of SequenceElements to fit them into SequenceVectors model
 *
 * @author raver119@gmail.com
 */
public class MySentenceTransformer implements SequenceTransformer<VocabWord, String>, Iterable<Sequence<VocabWord>>{
    /*
            So, we must accept any SentenceIterator implementations, and build vocab out of it, and use it for further transforms between text and Sequences
     */
    protected TokenizerFactory tokenizerFactory;
    protected DatabaseLabelledIterator iterator;
    protected boolean readOnly = false;
    protected AtomicInteger sentenceCounter = new AtomicInteger(0);
    protected VocabCache<VocabWord> vocabCache;

    private MySentenceTransformer(@NonNull DatabaseLabelledIterator iterator) {
        this.iterator = iterator;
    }

    @Override
    public Sequence<VocabWord> transformToSequence(String object) {
        throw new UnsupportedOperationException("TRYING TO CONVERT STRING INTO SEQUENCE BUT SEQUENCES ARE PREBUILT!");
    }

    public Sequence<VocabWord> transformToSequence(List<VocabWord> words, String label) {
        Sequence<VocabWord> sequence = new Sequence<>();

        for(VocabWord word : words) {
            sequence.addElement(word);
        }
        sequence.setSequenceId(sentenceCounter.getAndIncrement());
        sequence.setSequenceLabel(new VocabWord(1.0, label));
        return sequence;
    }

    @Override
    public Iterator<Sequence<VocabWord>> iterator() {
        iterator.reset();

        return new Iterator<Sequence<VocabWord>>() {
            @Override
            public boolean hasNext() {
                return MySentenceTransformer.this.iterator.hasNextDocument();
            }

            @Override
            public Sequence<VocabWord> next() {
                LabelledDocument document = iterator.nextDocument(vocabCache);
                if  (document.getReferencedContent() == null) return new Sequence<>();
                System.out.println(document.getLabel());
                Sequence<VocabWord> sequence = MySentenceTransformer.this.transformToSequence(document.getReferencedContent(),document.getLabel());
                return sequence;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static class Builder {
        protected TokenizerFactory tokenizerFactory;
        protected DatabaseLabelledIterator iterator;
        protected VocabCache<VocabWord> vocabCache;
        protected boolean readOnly = false;

        public Builder() {

        }

        public MySentenceTransformer.Builder tokenizerFactory(@NonNull TokenizerFactory tokenizerFactory) {
            this.tokenizerFactory = tokenizerFactory;
            return this;
        }

        public MySentenceTransformer.Builder iterator(@NonNull DatabaseLabelledIterator iterator) {
            this.iterator = iterator;
            return this;
        }

        public MySentenceTransformer.Builder vocabCache(@NonNull VocabCache<VocabWord> vocab) {
            this.vocabCache=vocab;
            return this;
        }

        public MySentenceTransformer.Builder readOnly(boolean readOnly) {
            this.readOnly = true;
            return this;
        }

        public MySentenceTransformer build() {
            MySentenceTransformer transformer = new MySentenceTransformer(this.iterator);
            transformer.tokenizerFactory = this.tokenizerFactory;
            transformer.readOnly = this.readOnly;
            transformer.vocabCache = this.vocabCache;
            return transformer;
        }
    }
}
