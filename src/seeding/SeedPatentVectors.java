package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import org.deeplearning4j.text.documentiterator.BasicLabelAwareIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import tools.WordVectorSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Created by ehallmark on 7/18/16.
 */
public class SeedPatentVectors {
    private int date;
    private static final File vocabFile = new File(Constants.VOCAB_FILE);
    private static final File paragraphVectorFile = new File(Constants.PARAGRAPH_VECTORS_FILE);
    private VocabCache<VocabWord> vocab;
    private ParagraphVectors paragraphVectors;
    private TokenizerFactory tokenizerFactory;
    private LabelAwareIterator iterator;

    public SeedPatentVectors(int startDate) throws Exception {
        this.date=startDate;

        // Tokenizer Factor
        tokenizerFactory = new DefaultTokenizerFactory();
        //tokenizerFactory.setTokenPreProcessor(str->str);

        // Create Iterator
        iterator = new BasicLabelAwareIterator.Builder(new BasePatentIterator(date))
                .build();

        // Create or load existing Vocabulary
        System.out.println("Getting Vocabulary...");
        if(!vocabFile.exists()) buildAndWriteVocab();
        else vocab = WordVectorSerializer.readVocabCache(vocabFile);

        // Start on ParagraphVectors
        System.out.println("Getting Paragraph Vectors...");
        if(!paragraphVectorFile.exists()) buildAndWriteParagraphVectors();
        else paragraphVectors = WordVectorSerializer.readParagraphVectorsFromText(paragraphVectorFile);

    }

    private void buildAndWriteVocab() throws IOException {
        vocab = new AbstractCache.Builder<VocabWord>()
                .hugeModelExpected(true)
                .minElementFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .build();

        SentenceTransformer transformer = new SentenceTransformer.Builder()
                .iterator(iterator)
                .tokenizerFactory(tokenizerFactory)
                .build();

        SequenceIterator<VocabWord> sequenceIterator = new AbstractSequenceIterator.Builder<>(transformer)
                .build();

        VocabConstructor<VocabWord> constructor = new VocabConstructor.Builder<VocabWord>()
                .setTargetVocabCache(vocab)
                .setStopWords(Arrays.asList(Constants.STOP_WORDS))
                .fetchLabels(false)
                .addSource(sequenceIterator, Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .build();

        constructor.buildJointVocabulary(false, true);
        WordVectorSerializer.writeVocabCache(vocab,vocabFile);
    }

    private void buildAndWriteParagraphVectors() throws IOException {
        WeightLookupTable<VocabWord> lookupTable = new InMemoryLookupTable.Builder<VocabWord>()
                .cache(vocab)
                .vectorLength(Constants.VECTOR_LENGTH)
                .useAdaGrad(false)
                .seed(41)
                .build();


        paragraphVectors = new ParagraphVectors.Builder()
                .useAdaGrad(false)
                .resetModel(false)
                .lookupTable(lookupTable)
                .vocabCache(vocab)
                .batchSize(1000)
                .trainWordVectors(true)
                .epochs(1)
                .iterations(5)
                .tokenizerFactory(tokenizerFactory)
                .windowSize(10)
                .iterate(iterator)
                .layerSize(Constants.VECTOR_LENGTH)
                .stopWords(Arrays.asList(Constants.STOP_WORDS))
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .learningRate(0.01)
                .minLearningRate(0.0001)
                .build();

        WordVectorSerializer.writeWordVectors(paragraphVectors, paragraphVectorFile);
    }

    public static void main(String[] args) {
        try {
            new SeedPatentVectors(20050000);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
