package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.learning.impl.sequence.DBOW;
import org.deeplearning4j.models.embeddings.loader.VectorsConfiguration;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import tools.WordVectorSerializer;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


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
            List<String> toAdd = Arrays.asList(preProcessor.preProcess(nextSentence).split("\\s+")).stream().filter(s->s!=null&&s.length()>0).collect(Collectors.toList());
            Database.insertRawPatent(label, toAdd);
        }

    }


    public static void main(String[] args) throws Exception {
        //Database.setupMainConn();
        Database.setupSeedConn();
        Database.setupInsertConn();
        //Database.setupCompDBConn();
        //File dataFolder = new File(Constants.RAW_PATENT_DATA_FOLDER);
        /*try {

            createDataFolder(new MyPreprocessor());
            Database.insertCommit();


        } finally {
            Database.close();
        }*/

        VocabCache<VocabWord> vocabCache;

        System.out.println("Checking existence of vocab file...");
        if(new File(Constants.VOCAB_FILE).exists()) {
            vocabCache = WordVectorSerializer.readVocab(new File(Constants.VOCAB_FILE));
        } else {
            System.out.println("Setting up iterator...");

            DatabaseLabelledIterator iterator = new DatabaseLabelledIterator();

            AbstractSequenceIterator<VocabWord> sequenceIterator = createSequenceIterator(iterator);

            System.out.println("Starting on vocab building...");


            vocabCache = new AbstractCache.Builder<VocabWord>()
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

            WordVectorSerializer.writeVocab(vocabCache, new File(Constants.VOCAB_FILE));
            System.out.println("Vocabulary finished...");
            new Emailer("Finished vocabulary!");

        }
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

        DatabaseLabelledIterator iterator = new DatabaseLabelledIterator(vocabCache);
        SequenceIterator<VocabWord> sequenceIterator = createSequenceIterator(iterator,vocabCache);

        System.out.println("Total number of documents: "+vocabCache.totalNumberOfDocs());

        System.out.println("Has patent 7436333_claim_1: + "+vocabCache.containsWord("7436333_claim_1"));
        System.out.println("Has word method: + "+vocabCache.containsWord("method")+" count "+vocabCache.wordFrequency("method"));


        // add word vectors

        System.out.println("Starting paragraph vectors...");
        ParagraphVectors vec = new ParagraphVectors.Builder()
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
                .stopWords(new ArrayList<String>())
                .trainElementsRepresentation(false)
                .trainSequencesRepresentation(true)
                //.elementsLearningAlgorithm(new SkipGram<>())
                //.sequenceLearningAlgorithm(new DBOW())
                .sampling(0.0001)
                //.negativeSample(5)
                .workers(4)
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
        WordVectorSerializer.writeWordVectors(vec, new File(Constants.WORD_VECTORS_PATH));

        System.out.println("Done...");

        System.out.println("Reading from file...");

        vec = WordVectorSerializer.readParagraphVectorsFromText(new File(Constants.WORD_VECTORS_PATH));

        double sim = vec.similarity("internet", "network");
        System.out.println("internet/computer similarity: " + sim);

        double sim2 = vec.similarity("internet", "protein");
        System.out.println("internet/protein similarity (should be lower): " + sim2);


        new Emailer("Finished paragraph vectors!");




    }

    private static AbstractSequenceIterator<VocabWord> createSequenceIterator(DatabaseLabelledIterator iterator, VocabCache<VocabWord> vocabCache) {
        System.out.println("Iterator transformation...");

        MySentenceTransformer transformer = new MySentenceTransformer.Builder()
                .iterator(iterator)
                .vocabCache(vocabCache)
                .build();

        System.out.println("Building sequence iterator from transformer...");

        return new AbstractSequenceIterator.Builder<>(transformer).build();
    }

    private static AbstractSequenceIterator<VocabWord> createSequenceIterator(DatabaseLabelledIterator iterator) {
        return createSequenceIterator(iterator,null);
    }
}
