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
public class SeedTFIDF {
    public static void main(String[] args) throws Exception{
        Database.setupSeedConn();
        Database.setupInsertConn();
        VocabCache<VocabWord> vocabCache;
        final File vocabFile = new File(Constants.STEMMED_VOCAB_FILE);
        DatabaseLabelledIterator iterator = new DatabaseLabelledIterator(true);
        System.out.println("Checking existence of vocab file...");

        if (vocabFile.exists()) {
            vocabCache = WordVectorSerializer.readVocab(vocabFile);
            assert vocabCache.totalNumberOfDocs() > 0 : "NO DOCUMENTS FOUND!";
        } else {
            throw new RuntimeException("NO VOCAB FILE!!!");
        }

        int numStopWords = Constants.NUM_STOP_WORDS;
        Set<String> stopWords = new HashSet<>(vocabCache.vocabWords().stream().sorted((w1, w2)->Double.compare(w2.getElementFrequency(),w1.getElementFrequency())).map(vocabWord->vocabWord.getLabel()).collect(Collectors.toList()).subList(0,numStopWords));
        // get middle words from vocab
        iterator.setVocabAndStopWords(vocabCache,stopWords);
        List<VocabWord> words = vocabCache.vocabWords().stream().filter(vw->!stopWords.contains(vw.getLabel())||vw.getElementFrequency()>= Constants.DEFAULT_MIN_WORD_FREQUENCY).collect(Collectors.toList());

        Float[] tfidfCounts = new Float[words.size()];
        AtomicInteger cnt = new AtomicInteger(0);

        ResultSet rs = Database.selectBOW();

        while(rs.next()) {
            String name = rs.getString(1);
            Integer[] array = (Integer[])rs.getArray(2).getArray();
            Arrays.fill(tfidfCounts, 0.0f);
            Map<Integer,Integer> indicesToCheck = new HashMap<>();
            for(int i = 0; i < array.length; i++) {
                if(array[i]>0) {
                    indicesToCheck.put(i,(int)words.get(i).getSequencesCount());
                }
            }
            indicesToCheck.entrySet().forEach(entry->{
                tfidfCounts[entry.getKey()]=new Float(new Double(array[entry.getKey()])*new Double(Math.log(new Double(vocabCache.totalNumberOfDocs())/entry.getValue())));
            });
            Database.updateTFIDF(name, tfidfCounts);
            System.out.println(cnt.getAndIncrement());
            if(cnt.get()%1000==0) Database.insertCommit();
        }
        Database.insertCommit();
        Database.close();
    }
}
