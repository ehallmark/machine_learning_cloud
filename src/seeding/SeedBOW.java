package seeding;

import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor;
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache;
import tools.Emailer;
import tools.WordVectorSerializer;

import java.io.File;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/29/16.
 */
public class SeedBOW {
    public static void main(String[] args) throws Exception{
        Database.setupSeedConn();
        Database.setupInsertConn();
        VocabCache<VocabWord> vocabCache;
        final File vocabFile = new File(Constants.STEMMED_VOCAB_FILE);
        DatabaseLabelledIterator iterator = new DatabaseLabelledIterator(true);
        System.out.println("Checking existence of vocab file...");

        if (vocabFile.exists()) {
            vocabCache = WordVectorSerializer.readVocab(vocabFile);
        } else {
            System.out.println("Setting up iterator...");
            AbstractSequenceIterator<VocabWord> sequenceIterator = BuildParagraphVectors.createSequenceIterator(iterator);

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

            WordVectorSerializer.writeVocab(vocabCache, vocabFile);
            System.out.println("Vocabulary finished...");
            sequenceIterator.reset();
            new Emailer("Finished vocabulary!");

        }


        if(vocabCache.totalNumberOfDocs() <= 0) {
            Set<String> patentSet = new HashSet<>();
            ResultSet rs = Database.selectRawPatentNames();
            AtomicInteger cnt = new AtomicInteger(0);
            while(rs.next()) {
                String patent = rs.getString(1).split("_")[0];
                if (!patentSet.contains(patent)) {
                    vocabCache.incrementTotalDocCount(1);
                    patentSet.add(patent);
                }
                System.out.println(cnt.getAndIncrement());
            }
            WordVectorSerializer.writeVocab(vocabCache, vocabFile);
            System.out.println("Vocabulary finished...");
        }

        System.out.println("Total number of documents: "+vocabCache.totalNumberOfDocs());
        System.out.println("Total number of words: "+vocabCache.numWords());
        System.out.println("Has word method: + "+vocabCache.containsWord("method")+" count "+vocabCache.wordFrequency("method"));

        int numStopWords = Constants.NUM_STOP_WORDS;
        Set<String> stopWords = new HashSet<>(vocabCache.vocabWords().stream().sorted((w1, w2)->Double.compare(w2.getElementFrequency(),w1.getElementFrequency())).map(vocabWord->vocabWord.getLabel()).collect(Collectors.toList()).subList(0,numStopWords));
        // get middle words from vocab
        iterator.setVocabAndStopWords(vocabCache,stopWords);
        List<String> words = vocabCache.vocabWords().stream().filter(vw->(!stopWords.contains(vw.getLabel()))&&vw.getElementFrequency()>= Constants.DEFAULT_MIN_WORD_FREQUENCY&&vw.getSequencesCount()<vocabCache.totalNumberOfDocs()).map(vw->vw.getLabel()).collect(Collectors.toList());
        SequenceIterator<VocabWord> sequenceIterator = BuildParagraphVectors.createSequenceIterator(iterator);
        Integer[] counts = new Integer[words.size()];
        //Float[] tfidfCounts = new Float[words.size()];
        AtomicInteger cnt = new AtomicInteger(0);
        while(sequenceIterator.hasMoreSequences()) {
            Sequence<VocabWord> sequence = sequenceIterator.nextSequence();
            String name = sequence.getSequenceLabel().getLabel();
            Arrays.fill(counts, 0);
            //Arrays.fill(tfidfCounts, 0.0f);
            //Map<Integer,Integer> indicesToCheck = new HashMap<>();
            for(VocabWord vw : sequence.getElements()) {
                int idx = words.indexOf(vw.getLabel());
                if(idx >= 0) {
                    counts[idx]++;
                    //indicesToCheck.put(idx,(int)Math.round(vw.getElementFrequency()));
                }
            }
            /*indicesToCheck.entrySet().forEach(entry->{
                tfidfCounts[entry.getKey()]=new Float(Math.log(new Double(vocabCache.totalNumberOfDocs())/entry.getValue()));
            });*/
            Database.insertBOW(name, counts);
            System.out.println(cnt.getAndIncrement());
            if(cnt.get()%1000==0) Database.insertCommit();
        }
        Database.insertCommit();
        Database.close();
    }
}
