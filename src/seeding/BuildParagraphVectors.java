package seeding;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.learning.impl.sequence.DBOW;
import org.deeplearning4j.models.embeddings.loader.VectorsConfiguration;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.Word2Vec;
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
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
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
        if(new File(Constants.VOCAB_FILE_WITH_LABELS).exists()) {
            vocabCache = WordVectorSerializer.readVocab(new File(Constants.VOCAB_FILE_WITH_LABELS));
        } else {
            if (new File(Constants.VOCAB_FILE).exists()) {
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

            // test
            if(!vocabCache.containsWord("6509257")) {
                System.out.println("We dont have patent 6509257 but we should... get labels again");
                // add special labels of each text
                ResultSet rs = Database.selectRawPatentNames();
                AtomicInteger i = new AtomicInteger(0);
                while (rs.next()) {
                    String patent = rs.getString(1).split("_")[0];
                    VocabWord word = new VocabWord(1.0, patent);
                    word.setSequencesCount(1);
                    word.setSpecial(true);
                    word.markAsLabel(true);
                    vocabCache.addToken(word);
                    if(!vocabCache.hasToken(patent)) {
                        word.setIndex(vocabCache.numWords());
                        vocabCache.addWordToIndex(word.getIndex(), patent);
                    }
                    vocabCache.incrementTotalDocCount(1);
                    System.out.println(i.getAndIncrement());
                }
                System.out.println("Writing vocab...");

                WordVectorSerializer.writeVocab(vocabCache, new File(Constants.VOCAB_FILE_WITH_LABELS));

            }
        }

        System.out.println("Total number of documents: "+vocabCache.totalNumberOfDocs());

        System.out.println("Has patent 6509257: + "+vocabCache.containsWord("6509257"));
        System.out.println("Has word method: + "+vocabCache.containsWord("method")+" count "+vocabCache.wordFrequency("method"));


        StringJoiner toEmail = new StringJoiner("\n");
        toEmail.add("Total number of documents: "+vocabCache.totalNumberOfDocs())
                .add("Has word method: + "+vocabCache.containsWord("method")+" count "+vocabCache.wordFrequency("method"));
        //new Emailer(toEmail.toString());


        DatabaseLabelledIterator iterator = new DatabaseLabelledIterator(vocabCache);
        SequenceIterator<VocabWord> sequenceIterator = createSequenceIterator(iterator);

        double negativeSampling = 0.0;

        WeightLookupTable<VocabWord> lookupTable = new InMemoryLookupTable.Builder<VocabWord>()
                .seed(41)
                .negative(negativeSampling)
                .vectorLength(Constants.VECTOR_LENGTH)
                .useAdaGrad(false)
                .cache(vocabCache)
                .build();

         /*
             reset model is viable only if you're setting AbstractVectors.resetModel() to false
             if set to True - it will be called internally
        */
        lookupTable.resetWeights(true);


        /*while(sequenceIterator.hasMoreSequences()) {
            Sequence<VocabWord> seq = sequenceIterator.nextSequence();
            assert(seq!=null) : "Sequence itself is NULL!!!";
            assert(seq.getSequenceLabel()!=null) : "Sequence label is NULL!!!!";
            assert seq.getElements()!=null : "Sequence has NULL Elements pointer!";
            assert seq.getElements().size() > 0 : "Sequence "+seq.getSequenceLabel()+" has no elements!";
            System.out.println(seq.getSequenceLabel()+": "+seq.getElements().size());
        }


        sequenceIterator.reset();
        */

        // add word vectors
        int numStopWords = 500;
        List<VocabWord> stopWords = vocabCache.vocabWords().stream().sorted((w1,w2)->Double.compare(w2.getElementFrequency(),w1.getElementFrequency())).collect(Collectors.toList()).subList(0,numStopWords);
        StringJoiner join = new StringJoiner("\n");
        join.add("Stop words: ");
        for(VocabWord w: stopWords) {
            join.add(w.getLabel()+": "+w.getElementFrequency());
        }

        double sampling = 0.0001;

        System.out.println("Starting word vectors...");
        // train words
        Word2Vec wordVectors = new Word2Vec.Builder()
                .seed(41)
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .iterate(sequenceIterator)
                .batchSize(500)
                .layerSize(Constants.VECTOR_LENGTH)
                .epochs(1)
                .negativeSample(negativeSampling)
                .iterations(5)
                .sampling(sampling)
                .resetModel(false)
                .minLearningRate(0.0001)
                .learningRate(0.005)
                .workers(4)
                .windowSize(Constants.MIN_WORDS_PER_SENTENCE)
                .vocabCache(vocabCache)
                .lookupTable(lookupTable)
                .build();
        wordVectors.fit();

        new Test(lookupTable, true);
        sequenceIterator.reset();


        System.out.println("Starting paragraph vectors...");
        ParagraphVectors vec = new ParagraphVectors.Builder()
                .seed(41)
                .minWordFrequency(Constants.DEFAULT_MIN_WORD_FREQUENCY)
                .iterations(5)
                .epochs(1)
                .layerSize(Constants.VECTOR_LENGTH)
                .learningRate(0.005)
                .minLearningRate(0.0001)
                .batchSize(500)
                .windowSize(Constants.MIN_WORDS_PER_SENTENCE)
                .iterate(sequenceIterator)
                .vocabCache(vocabCache)
                .lookupTable(lookupTable)
                .resetModel(false)
                .stopWords(stopWords)
                .trainElementsRepresentation(false)
                .trainSequencesRepresentation(true)
                //.elementsLearningAlgorithm(new SkipGram<>())
                //.sequenceLearningAlgorithm(new DBOW())
                .sampling(sampling)
                .negativeSample(negativeSampling)
                .workers(4)
                .build();

        System.out.println("Starting to train paragraph vectors...");
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


        new Test(vec.lookupTable());


    }

    private static AbstractSequenceIterator<VocabWord> createSequenceIterator(DatabaseLabelledIterator iterator) {
        System.out.println("Iterator transformation...");

        MySentenceTransformer transformer = new MySentenceTransformer.Builder().iterator(iterator).build();

        System.out.println("Building sequence iterator from transformer...");

        return new AbstractSequenceIterator.Builder<>(transformer).build();
    }
}
