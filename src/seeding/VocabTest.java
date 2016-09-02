package seeding;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 9/2/16.
 */
public class VocabTest {
    public static void main(String[] args) throws Exception {
        Map<String,Float> vocab = BuildVocabulary.readVocabMap(new File(Constants.VOCAB_MAP_FILE));
        List<String> testWords = Arrays.asList("nuclear","internet","claims","network","semiconductor","gambling","repudiate");
        testWords.forEach(word->{
            Float freq = vocab.containsKey(word) ? vocab.get(word) : 0.0f;
            System.out.println("Inverse Document Frequency for "+word+": "+freq.toString());
        });
    }
}
