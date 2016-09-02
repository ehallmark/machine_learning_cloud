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
public class BuildVocabulary {
    public static TokenizerFactory tokenizerFactory;
    static {
        tokenizerFactory = new DefaultTokenizerFactory();
        tokenizerFactory.setTokenPreProcessor(new MyPreprocessor());
    }

    public static void main(String[] args) throws SQLException {
        Database.setupSeedConn();

        File vocabFile = new File(Constants.VOCAB_MAP_FILE);
        Map<String,AtomicInteger> vocab = new HashMap<>();
        System.out.println("Starting on vocab building...");

        ResultSet rs = Database.selectPatentVectors(Database.getValuablePatentsToList());
        AtomicLong totalDocumentCount = new AtomicLong(0);
        while(rs.next()) {
            totalDocumentCount.getAndIncrement();
            System.out.println(totalDocumentCount.get());
            int offset = 2;
            Set<String> updatedThisDocument = new HashSet<>();
            // each row is a "Document"
            String abstractText = rs.getString(offset);
            helper(updatedThisDocument,abstractText,vocab);
            String description = rs.getString(offset+1);
            helper(updatedThisDocument,description,vocab);
            String claims = rs.getString(offset+2);
            helper(updatedThisDocument,claims,vocab);

        }
        Map<String,Float> vocabFloat = new HashMap<>();
        for(Map.Entry<String,AtomicInteger> e : vocab.entrySet()) {
            // IDF
            vocabFloat.put(e.getKey(),(float)Math.log(new Double(totalDocumentCount.get())/e.getValue().get()));
        }
        try {
            writeVocabMap(vocabFloat, vocabFile);
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

