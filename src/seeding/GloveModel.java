package seeding;

import org.deeplearning4j.models.glove.Glove;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.sentenceiterator.SentenceIterator;
import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import org.deeplearning4j.text.sentenceiterator.interoperability.SentenceIteratorConverter;
import tools.WordVectorSerializer;

import java.io.File;
import java.util.Collection;

/**
 * Created by ehallmark on 8/25/16.
 */
public class GloveModel {
    public static void main(String[] args) throws Exception {
        // Split on white spaces in the line to get words
        Database.setupSeedConn();

        SequenceIterator<VocabWord> iterator = BuildParagraphVectors.createSequenceIterator(new EtsiLabelledIterator());

        Glove glove = new Glove.Builder()
                .iterate(iterator)
                //.tokenizerFactory(t)

                .alpha(0.75)
                .learningRate(0.1)
                // number of epochs for training
                .epochs(1)
                // cutoff for weighting function
                .xMax(100)
                // training is done in batches taken from training corpus
                .batchSize(1000)
                // if set to true, batches will be shuffled before training
                .shuffle(true)
                // if set to true word pairs will be built in both directions, LTR and RTL
                .symmetric(true)
                .build();

        glove.fit();

        WordVectorSerializer.writeWordVectors(glove, new File("glove.txt"));

        double simD = glove.similarity("cellular", "wireless");
        System.out.println("cellular/wireless similarity: " + simD);

        Collection<String> words = glove.wordsNearest("semiconductor", 10);
        System.out.println("Nearest words to 'semiconductor': " + words);
    }
}
