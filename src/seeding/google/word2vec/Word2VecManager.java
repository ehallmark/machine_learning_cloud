package seeding.google.word2vec;

import com.google.gson.Gson;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class Word2VecManager {
    private static Word2Vec MODEL;
    public static synchronized Word2Vec getOrLoadManager() {
        if(MODEL==null) {
            String word2VecPath = new File("data/word2vec_model_large.nn256").getAbsolutePath();
            MODEL = WordVectorSerializer.readWord2VecModel(word2VecPath);
        }
        return MODEL;
    }

    public static Map<String, Collection<String>> synonymsFor(List<String> words, int n, double minSimilarity) {
        Word2Vec word2Vec = getOrLoadManager();
        Map<String, Collection<String>> similarityMap = new HashMap<>();
        for (String word : words) {
            Collection<String> similar = word2Vec.wordsNearest(word, n);
            similar = similar.stream().filter(sim->word2Vec.similarity(word, sim) >= minSimilarity).collect(Collectors.toList());
            similarityMap.put(word, similar);
        }
        return similarityMap;
    }


    public static final void main(String[] args) {
        // testing
        Map<String, Collection<String>> results = synonymsFor(Arrays.asList("semiconductor", "medecine", "automotive"), 10, 0.75);
        System.out.println("Results: "+new Gson().toJson(results));
    }
}
