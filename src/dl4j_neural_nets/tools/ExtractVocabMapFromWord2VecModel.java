package dl4j_neural_nets.tools;

import dl4j_neural_nets.vectorization.WordVectorModel;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 12/3/16.
 */
public class ExtractVocabMapFromWord2VecModel {
    public static Map<String,Pair<Float,INDArray>> saveAndExtract(Word2Vec model, File toSave) throws IOException {
        Map<String,Pair<Float,INDArray>> vocabMap = new HashMap<>();
        WeightLookupTable<VocabWord> lookupTable = model.getLookupTable();
        model.getVocab().vocabWords().forEach(word->{
            vocabMap.put(word.getWord(),new Pair<>((float)word.getElementFrequency(),lookupTable.vector(word.getWord())));
        });
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(toSave)));
        oos.writeObject(vocabMap);
        oos.flush();
        oos.close();
        return vocabMap;
    }

    public static Map<String,Pair<Float,INDArray>> readVocabMap(File vocabFile) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(vocabFile)));
        Map<String,Pair<Float,INDArray>> map = (Map<String,Pair<Float,INDArray>>)ois.readObject();
        ois.close();
        return map;
    }

    public static void main(String[] args) throws Exception{
        File vocabFile = new File("data/my_custom_vocab_map_file.vocab");
        int epoch = 1;
        Word2Vec net = WordVectorModel.load(WordVectorModel.wordVectorFile.getAbsolutePath()+epoch);
        ExtractVocabMapFromWord2VecModel.saveAndExtract(net,vocabFile);
    }
}
