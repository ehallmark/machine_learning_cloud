package semantic_hash;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;


import org.deeplearning4j.text.sentenceiterator.labelaware.LabelAwareSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import seeding.Constants;
import seeding.MyPreprocessor;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Created by ehallmark on 6/13/16.
 */
public class Training {

    private ParagraphVectors paragraphVectors;
    private LabelAwareSentenceIterator iterator;
    private TokenizerFactory tokenizerFactory;

    public static void main(String[] args) throws Exception {

        Training app = new Training();
        app.makeParagraphVectors();
        app.saveParagraphVectors();
    }

    private void saveParagraphVectors() throws IOException {
        // dont overwrite
        File pVectors = new File("SemanticHashParagraphVectors.txt");
        if(pVectors.exists())pVectors.delete();
        // Write word vectors
        WordVectorSerializer.writeWordVectors(paragraphVectors, new File("SemanticHashParagraphVectors.txt"));

    }

    void makeParagraphVectors()  throws Exception {
        // build a iterator for our dataset
        iterator = new DatabaseLabelAwareIterator(50000,1000);

        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());

        // ParagraphVectors training configuration
        paragraphVectors = new ParagraphVectors.Builder()
                .learningRate(0.001)
                .minLearningRate(0.0001)
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .epochs(5)
                .batchSize(1000)
                .windowSize(5)
                .iterations(3)
                .iterate(iterator)
                .layerSize(500)
                .stopWords(Arrays.asList(Constants.STOP_WORDS))
                .trainWordVectors(true)
                .trainElementsRepresentation(false)
                .trainSequencesRepresentation(true)
                .tokenizerFactory(tokenizerFactory)
                .build();

        // Start model training
        paragraphVectors.fit();

    }


}

