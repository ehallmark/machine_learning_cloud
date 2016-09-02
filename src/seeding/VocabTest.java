package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.wordstore.VocabCache;
import tools.WordVectorSerializer;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 9/2/16.
 */
public class VocabTest {
    public static void main(String[] args) throws Exception {
        WordVectors vectors = WordVectorSerializer.loadGoogleModel(new File(Constants.GOOGLE_WORD_VECTORS_PATH),true);
        VocabCache<VocabWord> vocab = vectors.vocab();
        double n = new Double(vocab.totalNumberOfDocs());
        List<String> testWords = Arrays.asList("nuclear","internet","claims","network","semiconductor","gambling","repudiate");
        testWords.forEach(word->{
            Double freq = vocab.hasToken(word) ? Math.log(n/vocab.tokenFor(word).getSequencesCount()) : 0.0d;
            System.out.println("Inverse Document Frequency for "+word+": "+freq.toString());
        });

    }
}
