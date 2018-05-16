package python_compatibility.rnn_enc;

import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.indexing.NDArrayIndex;
import seeding.google.word2vec.Word2VecManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class IngestWord2VecToText {

    public static void main(String[] args) throws Exception {
        Word2Vec word2Vec = Word2VecManager.getOrLoadManager();
        {
            BufferedWriter bw = new BufferedWriter(new FileWriter(new File("/home/ehallmark/Downloads/word2vec256_index.txt")));
            Set<String> words = new HashSet<>();
            Set<Integer> indices = new HashSet<>();
            word2Vec.vocab().vocabWords().forEach(word -> {
                words.add(word.getLabel());
                indices.add(word.getIndex());
                try {
                    bw.write(word.getLabel() + "," + word.getIndex() + "," + word.getElementFrequency() + "\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            bw.flush();
            bw.close();

            System.out.println("Num words: "+words.size());
            System.out.println("Num indices: "+indices.size());
            System.out.println("Vocab size: "+word2Vec.getVocab().numWords());
        }
        {
            BufferedWriter bw = new BufferedWriter(new FileWriter(new File("/home/ehallmark/Downloads/word2vec256_vectors.txt")));
            INDArray weights = word2Vec.getLookupTable().getWeights();
            for(int i = 0; i < weights.rows(); i++) {
                INDArray row = weights.getRow(i);
                double[] d = row.data().asDouble();
                for(int j = 0; j < d.length; j++) {
                    double x = d[j];
                    bw.write(String.valueOf(x));
                    if(j < d.length-1) {
                        bw.write(" ");
                    }
                }
                bw.write("\n");
            }
            for(int n = 0; n < 2; n++) {
                for (int i = 0; i < word2Vec.getLayerSize(); i++) {
                    bw.write("0");
                    if (i < word2Vec.getLayerSize() - 1) {
                        bw.write(" ");
                    }
                }
                bw.write("\n");
            }
            bw.flush();
            bw.close();
        }
    }
}
