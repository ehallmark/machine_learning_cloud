package seeding.google.word2vec;

import com.google.gson.Gson;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import seeding.Constants;
import seeding.Database;
import synonyms.ThesaurusCSVBuilder;

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
            System.out.println("Loading word to count map...");
            getWordToCountMap();
            System.out.println("Loading thesaurus...");
            getDefaultThesaurus();
            System.out.println("Finished loading word2vec manager.");
        }
        return MODEL;
    }

    public static Map<String, Collection<String>> synonymsFor(List<String> words, int n, double minSimilarity) {
        ThesaurusCSVBuilder thesaurus = getDefaultThesaurus();
        Map<String, Collection<String>> similarityMap = new HashMap<>();
        String[] context = words.toArray(new String[words.size()]);
        for (String word : words) {
            Collection<String> similar = thesaurus.synonymsFor(word, minSimilarity, context).stream().filter(sim->{
                System.out.println("Found: "+sim);
                return getWordToCountMap().containsKey(sim);
            }).map(s->s.contains(" ")?("\""+s+"\""):s).limit(n).collect(Collectors.toList());
            similarityMap.put(word, similar);
        }
        return similarityMap;
    }

    private static Map<String,Integer> wordToCountMap;
    public static synchronized Map<String,Integer> getWordToCountMap() {
        if(wordToCountMap == null) {
            wordToCountMap = Collections.synchronizedMap(new HashMap<>());
            Connection conn = Database.getConn();
            try {
                PreparedStatement ps = conn.prepareStatement("select keyword, doc_count from big_query_keyword_count_helper");
                ResultSet rs = ps.executeQuery();
                while(rs.next()) {
                    String word = rs.getString(1);
                    if (!Constants.CLAIM_STOP_WORD_SET.contains(word)) {
                        wordToCountMap.put(word, rs.getInt(2));
                    }
                }
                rs.close();
                ps.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return wordToCountMap;
    }

    private static ThesaurusCSVBuilder THESAURUS;
    public static synchronized ThesaurusCSVBuilder getDefaultThesaurus() {
        if(THESAURUS==null) {
            THESAURUS = new ThesaurusCSVBuilder();
            // test
            THESAURUS.synonymsFor("test", 0, new String[]{"context", "words"});
        }
        return THESAURUS;
    }


    public static final void main(String[] args) {
        // testing
        Map<String, Collection<String>> results = synonymsFor(Arrays.asList("semiconductor", "medicine", "medecine", "automotive", "virtual", "reality", "artificial", "semiconductor", "patent", "gsm", "aircraft", "uav", "drone", "speech", "database", "neural", "aquatic"), 10, 0.0);
        System.out.println("Results: "+new Gson().toJson(results));
    }
}
