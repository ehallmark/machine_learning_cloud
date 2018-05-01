package seeding.google.word2vec;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;

import java.io.File;

public class Word2VecManager {
    private static Word2Vec MODEL;
    public static synchronized Word2Vec getOrLoadManager() {
        if(MODEL==null) {
            String word2VecPath = new File("data/word2vec_model_large.nn256").getAbsolutePath();
            MODEL = WordVectorSerializer.readWord2VecModel(word2VecPath);
        }
        return MODEL;
    }
}
