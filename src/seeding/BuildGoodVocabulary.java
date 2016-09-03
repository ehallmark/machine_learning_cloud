package seeding;

import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import tools.Emailer;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 9/1/16.
 */
public class BuildGoodVocabulary {
    public static TokenizerFactory tokenizerFactory;
    static {
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static void main(String[] args) throws Exception {
        Database.setupSeedConn();

        File vocabFile = new File(Constants.GOOD_VOCAB_MAP_FILE);
        Map<String,AtomicInteger> vocab = new HashMap<>();
        Map<String,AtomicInteger> subClassVocab = new HashMap<>();
        System.out.println("Starting on vocab building...");

        // Multiply by word count in main class * log of word count in sub class

        ResultSet rs = Database.selectMainClassWords();
        AtomicLong totalDocumentCount = new AtomicLong(0);
        while(rs.next()) {
            System.out.println(totalDocumentCount.getAndIncrement());
            int offset = 1;
            Set<String> updatedThisDocument = new HashSet<>();
            // each row is a "Document"
            String words = rs.getString(offset);
            helper(updatedThisDocument,words,vocab);

        }
        rs.close();

        ResultSet rs2 = Database.selectSubClassWords();
        totalDocumentCount.set(0);
        while(rs.next()) {
            System.out.println(totalDocumentCount.getAndIncrement());
            int offset = 1;
            Set<String> updatedThisDocument = new HashSet<>();
            // each row is a "Document"
            String words = rs.getString(offset);
            helper(updatedThisDocument,words,subClassVocab);

        }
        rs2.close();

        Map<String,Float> oldMap = BuildVocabulary.readVocabMap(new File(Constants.VOCAB_MAP_FILE));

        for(Map.Entry<String,AtomicInteger> e : vocab.entrySet()) {
            if(oldMap.containsKey(e.getKey())) {
                oldMap.put(e.getKey(), oldMap.get(e.getKey())*e.getValue().get());
            }
        }

        for(Map.Entry<String,AtomicInteger> e : subClassVocab.entrySet()) {
            if(oldMap.containsKey(e.getKey())) {
                oldMap.put(e.getKey(), (float) (oldMap.get(e.getKey())*Math.log(e.getValue().get())));
            }
        }

        try {
            writeVocabMap(oldMap, vocabFile);
            System.out.println("Vocabulary written to file...");
        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.println("Vocabulary finished...");
        new Emailer("Finished vocabulary!");
        Database.close();
    }

    public static void helper(Set<String> updatedThisDocument, String text, Map<String,AtomicInteger> vocab) {
        for(String token : tokenizerFactory.create(text).getTokens()) {
            if(Constants.STOP_WORD_SET.contains(token)) continue;
            if(!updatedThisDocument.contains(token)) {
                updatedThisDocument.add(token);
                // check if word already exists in vocab
                if(vocab.containsKey(token)) {
                    vocab.get(token).incrementAndGet();
                } else {
                    vocab.put(token, new AtomicInteger(1));
                }
            }
        }
    }

    public static Map<String,Float> readVocabMap(File vocabFile) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(vocabFile)));
        Map<String,Float> map = (Map<String,Float>)ois.readObject();
        ois.close();
        return map;
    }

    public static void writeVocabMap(Map<String,Float> vocab, File vocabFile) throws Exception {
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(vocabFile)));
        oos.writeObject(vocab);
        oos.flush();
        oos.close();
    }
}

