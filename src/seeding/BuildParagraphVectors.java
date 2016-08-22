package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.learning.impl.sequence.DBOW;
import org.deeplearning4j.models.embeddings.loader.VectorsConfiguration;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.serialization.VocabWordFactory;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;

import org.deeplearning4j.text.sentenceiterator.SentencePreProcessor;
import tools.Emailer;

import java.io.File;
import java.util.Arrays;


/**
 * Created by ehallmark on 8/16/16.
 */
public class BuildParagraphVectors {

    public static void createDataFolder(SentencePreProcessor preProcessor) throws Exception {
        //if(!folder.exists())folder.mkdirs();

        BasePatentIterator iter = new BasePatentIterator(Constants.START_DATE);
        iter.reset();
        while(iter.hasNext()) {
            String nextSentence = iter.nextSentence();
            String label = iter.currentLabel();
            /*File toMake = new File(folder.getAbsolutePath()+"/"+label);
            if(!toMake.exists()) {
                BufferedWriter bw = new BufferedWriter(new FileWriter(toMake));
                bw.write(preProcessor.preProcess(nextSentence));
                bw.flush();
                bw.close();
            }*/
            Database.insertRawPatent(label, Arrays.asList(preProcessor.preProcess(nextSentence).split("\\s+")));
        }

    }


    public static void main(String[] args) throws Exception {
        Database.setupMainConn();
        Database.setupSeedConn();
        Database.setupInsertConn();
        Database.setupCompDBConn();
        //File dataFolder = new File(Constants.RAW_PATENT_DATA_FOLDER);
        try {

            createDataFolder(new MyPreprocessor());
            Database.insertCommit();


        } finally {
            Database.close();
        }

        DatabaseLabelledIterator iterator = new DatabaseLabelledIterator();
        MySentenceTransformer transformer = new MySentenceTransformer.Builder()
                .iterator(iterator)
                .build();

        AbstractSequenceIterator<VocabWord> sequenceIterator = new AbstractSequenceIterator.Builder<>(transformer).build();

        VocabCache<VocabWord> vocabCache = new AbstractCache.Builder<VocabWord>()
                .hugeModelExpected(true)
                .minElementFrequency(Constants.MIN_WORDS_PER_SENTENCE)
                .build();

        /*
            Now we should build vocabulary out of sequence iterator.
            We can skip this phase, and just set AbstractVectors.resetModel(TRUE), and vocabulary will be mastered internally
        */
        VocabConstructor<VocabWord> constructor = new VocabConstructor.Builder<VocabWord>()
                .addSource(sequenceIterator, Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .setTargetVocabCache(vocabCache)
                .build();

        constructor.buildJointVocabulary(false, true);

        WeightLookupTable<VocabWord> lookupTable = new InMemoryLookupTable.Builder<VocabWord>()
                .seed(41)
                .vectorLength(Constants.VECTOR_LENGTH)
                .useAdaGrad(false)
                .cache(vocabCache)
                .build();

         /*
             reset model is viable only if you're setting AbstractVectors.resetModel() to false
             if set to True - it will be called internally
        */
        lookupTable.resetWeights(true);

        System.out.println("Starting paragraph vectors...");
        SequenceVectors<VocabWord> vec = new SequenceVectors.Builder(new VectorsConfiguration())
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .iterations(3)
                .epochs(1)
                .layerSize(Constants.VECTOR_LENGTH)
                .learningRate(0.05)
                .minLearningRate(0.001)
                .batchSize(1000)
                .windowSize(5)
                .iterate(sequenceIterator)
                .vocabCache(vocabCache)
                .lookupTable(lookupTable)
                .resetModel(false)
                .trainElementsRepresentation(true)
                .trainSequencesRepresentation(true)
                .sequenceLearningAlgorithm(new DBOW<VocabWord>())
                .elementsLearningAlgorithm(new SkipGram<VocabWord>())
                .sampling(0.0001)
                .negativeSample(5)
                .workers(6)
                .build();

        vec.fit();

        System.out.println("Finished paragraph vectors...");
        Database.close();


        /*
            In training corpus we have few lines that contain pretty close words invloved.
            These sentences should be pretty close to each other in vector space
            line 3721: This is my way .
            line 6348: This is my case .
            line 9836: This is my house .
            line 12493: This is my world .
            line 16393: This is my work .
            this is special sentence, that has nothing common with previous sentences
            line 9853: We now have one .
            Note that docs are indexed from 0
         */

        System.out.println("Writing to file...");
        WordVectorSerializer.writeSequenceVectors(vec, new VocabWordFactory(), new File(Constants.WORD_VECTORS_PATH));

        System.out.println("Done...");

        System.out.println("Reading from file...");

        vec = WordVectorSerializer.readSequenceVectors(new VocabWordFactory(), new File(Constants.WORD_VECTORS_PATH));

        double sim = vec.similarity("internet", "network");
        System.out.println("internet/computer similarity: " + sim);

        double sim2 = vec.similarity("internet", "protein");
        System.out.println("internet/protein similarity (should be lower): " + sim2);


        new Emailer("Finished paragraph vectors!");

        


    }
}
