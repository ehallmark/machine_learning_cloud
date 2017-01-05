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
        Map<String,AtomicInteger> legalTerms = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(new File(Constants.LEGAL_TERMS_FILE)));
        String line;
        AtomicLong legalTermsCount = new AtomicLong(0);
        while((line=br.readLine())!=null) {
            tokenizerFactory.create(line).getTokens().forEach(token->{
                legalTermsCount.getAndIncrement();
                if(legalTerms.containsKey(token)) {
                    legalTerms.get(token).getAndIncrement();
                } else {
                    legalTerms.put(token,new AtomicInteger(1));
                }
            });
        }
        br.close();

        Database.setupSeedConn();

        File vocabFile = new File(Constants.BETTER_VOCAB_MAP_FILE);
        Map<String,AtomicInteger> vocab = new HashMap<>();
        Map<String,AtomicInteger> subClassVocab = new HashMap<>();
        System.out.println("Starting on vocab building...");

        // Multiply by word count in main class * log of word count in sub class

        ResultSet rs = Database.selectMainClassWords();
        AtomicLong totalClassCount = new AtomicLong(0);
        while(rs.next()) {
            System.out.println(totalClassCount.getAndIncrement());
            int offset = 1;
            Set<String> updatedThisDocument = new HashSet<>();
            // each row is a "Document"
            String words = rs.getString(offset);
            helper(updatedThisDocument,words,vocab);

        }
        rs.close();
        AtomicLong totalSubClassCount = new AtomicLong(0);
        ResultSet rs2 = Database.selectSubClassWords();
        while(rs2.next()) {
            System.out.println(totalSubClassCount.getAndIncrement());
            int offset = 1;
            Set<String> updatedThisDocument = new HashSet<>();
            // each row is a "Document"
            String words = rs2.getString(offset);
            helper(updatedThisDocument,words,subClassVocab);

        }
        rs2.close();

        System.out.println("Reading old map...");
        Map<String,Float> oldMap = BuildVocabulary.readVocabMap(new File(Constants.VOCAB_MAP_FILE));
        Constants.STOP_WORD_SET.forEach(stopWord->{
            oldMap.remove(stopWord);
        });
        System.out.println("Updating old map...");

        for(Map.Entry<String,AtomicInteger> e : vocab.entrySet()) {
            if(oldMap.containsKey(e.getKey())&&!Constants.STOP_WORD_SET.contains(e.getKey())) {
                oldMap.put(e.getKey(), new Float(oldMap.get(e.getKey())+Math.log(new Double(totalClassCount.get())/e.getValue().get())));
            }
        }

        for(Map.Entry<String,AtomicInteger> e : subClassVocab.entrySet()) {
            if(oldMap.containsKey(e.getKey())&&!Constants.STOP_WORD_SET.contains(e.getKey())) {
                oldMap.put(e.getKey(), new Float(oldMap.get(e.getKey())+Math.log(new Double(totalSubClassCount.get())/e.getValue().get())));
            }
        }

        for(Map.Entry<String,AtomicInteger> e : legalTerms.entrySet()) {
            if(oldMap.containsKey(e.getKey())) {
                oldMap.put(e.getKey(),(float)(oldMap.get(e.getKey())/Math.log(Math.E+e.getValue().get())));
            }
        }

        System.out.println("Writing new map...");

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

