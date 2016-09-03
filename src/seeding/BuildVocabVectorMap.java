package seeding;

import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.nd4j.linalg.api.ndarray.INDArray;
import tools.WordVectorSerializer;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 9/3/16.
 */
public class BuildVocabVectorMap {
    public static void main(String[] args) throws Exception {
        File toWriteTo = new File(Constants.VOCAB_VECTOR_FILE);
        Map<String,Float> vocabMap = BuildVocabulary.readVocabMap(new File(Constants.GOOD_VOCAB_MAP_FILE));
        WordVectors wordVectors = WordVectorSerializer.loadGoogleModel(new File(Constants.GOOGLE_WORD_VECTORS_PATH),true);
        Map<String,Pair<Float,INDArray>> outputMap = new HashMap<>();
        vocabMap.entrySet().forEach(e->{
           if(wordVectors.hasWord(e.getKey())) {
               System.out.println(e.getKey());
               outputMap.put(e.getKey(),new Pair<>(e.getValue(),wordVectors.lookupTable().vector(e.getKey())));
           }
        });
        System.out.println("Writing to file...");
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(toWriteTo)));
        oos.writeObject(outputMap);
        oos.flush();
        oos.close();
    }

    public static Map<String,Pair<Float,INDArray>> readVocabMap(File vocabFile) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(vocabFile)));
        Map<String,Pair<Float,INDArray>> map = (Map<String,Pair<Float,INDArray>>)ois.readObject();
        ois.close();
        return map;
    }
}
