package dl4j_neural_nets.vectorization;

import org.deeplearning4j.models.glove.Glove;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.FileLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author raver119@gmail.com
 */
public class GloVeExample {

    private static final Logger log = LoggerFactory.getLogger(GloVeExample.class);

    public static void main(String[] args) throws Exception {
        File inputFile = new File("raw_sentences.txt");
        // Split on white spaces in the line to get words
        TokenizerFactory t = new DefaultTokenizerFactory();
        t.setTokenPreProcessor(new CommonPreprocessor());

        // creating SentenceIterator wrapping our training corpus
        LabelAwareIterator sentenceIter = //new BasicLineIterator(inputFile.getAbsolutePath());
        new FileLabelAwareIterator.Builder().addSourceFolder(new File("paravec/labeled/")).build();
        SentenceTransformer transformer = new SentenceTransformer.Builder()
                .iterator(sentenceIter)
                .tokenizerFactory(t)
                .build();

        SequenceIterator<VocabWord> iter = new AbstractSequenceIterator.Builder<>(transformer).build();

        Glove glove = new GlobalDocumentVectors.Builder()
                .iterate(iter)
                .tokenizerFactory(t)

                .useAdaGrad(true)

                .layerSize(50)

                .alpha(0.75)
                .learningRate(0.05)

                // number of epochs for training
                .epochs(25)
                .iterations(50)

                // cutoff for weighting function
                .xMax(100)

                .windowSize(10)

                // training is done in batches taken from training corpus
                .batchSize(10)

                // if set to true, batches will be shuffled before training
                .shuffle(true)

                .workers(4)

                // if set to true word pairs will be built in both directions, LTR and RTL
                .symmetric(true)
                .build();

        glove.fit();

        iter.reset();
        Set<String> labels = new HashSet<>();
        while(iter.hasMoreSequences()) {
            labels.addAll(iter.nextSequence().getSequenceLabels().stream().map(l->l.getLabel()).collect(Collectors.toList()));
        }

        labels.forEach(label->{
            Collection<String> words = glove.wordsNearest(label, 10);
            log.info("Nearest words to '"+label+"': " + words);
        });

        System.exit(0);
    }
}