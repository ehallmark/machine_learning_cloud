package seeding.google.word2vec;

import com.google.gson.Gson;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import seeding.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

public class Word2VecManager {
    private static Word2Vec MODEL;
    public static synchronized Word2Vec getOrLoadManager() {
        if(MODEL==null) {
            String word2VecPath = new File("data/word2vec_model_large.nn256").getAbsolutePath();
            MODEL = WordVectorSerializer.readWord2VecModel(word2VecPath);
            getWordToCountMap();
        }
        return MODEL;
    }

    public static Map<String, Collection<String>> synonymsFor(List<String> words, int n, double minSimilarity) {
        Word2Vec word2Vec = getOrLoadManager();
        Map<String, Collection<String>> similarityMap = new HashMap<>();
        for (String word : words) {
            Collection<String> similar = word2Vec.wordsNearest(word, n*2).stream().filter(sim->{
                return getWordToCountMap().containsKey(sim) && word2Vec.similarity(word,sim) >= minSimilarity;
            }).limit(n).collect(Collectors.toList());
            System.out.println("Similar to: "+word);

            for(String sim : similar) {
                System.out.println("  "+sim+": "+word2Vec.similarity(word, sim));
            }
            similarityMap.put(word, similar);
        }
        return similarityMap;
    }

    private static Map<String,Long> wordToCountMap;
    public static synchronized Map<String,Long> getWordToCountMap() {
        if(wordToCountMap == null) {
            wordToCountMap = Collections.synchronizedMap(new HashMap<>());
            Connection conn = Database.getConn();
            try {
                PreparedStatement ps = conn.prepareStatement("select keyword, doc_count::long from big_query_keyword_count_helper where num_words=1");
                ResultSet rs = ps.executeQuery();
                while(rs.next()) {
                    wordToCountMap.put(rs.getString(1), rs.getLong(2));
                }
                rs.close();
                ps.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return wordToCountMap;
    }


    public static final void main(String[] args) {
        // testing
        Map<String, Collection<String>> results = synonymsFor(Arrays.asList("semiconductor", "medicine", "medecine", "automotive", "virtual", "reality", "artificial", "semiconductor", "patent", "gsm", "aircraft", "uav", "drone", "speech", "database", "neural", "aquatic"), 10, 0.0);
        System.out.println("Results: "+new Gson().toJson(results));
    }
}
