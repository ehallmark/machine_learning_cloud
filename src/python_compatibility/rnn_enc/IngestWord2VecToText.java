package python_compatibility.rnn_enc;

import org.deeplearning4j.models.word2vec.Word2Vec;
import seeding.google.word2vec.Word2VecManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class IngestWord2VecToText {
    static final File indexFile = new File("/home/ehallmark/Downloads/word2vec256_index.txt");
    public static void main(String[] args) throws Exception {
        Word2Vec word2Vec = Word2VecManager.getOrLoadManager();
        {
            BufferedWriter bw = new BufferedWriter(new FileWriter(indexFile));
            Set<String> words = new HashSet<>();
            Set<Integer> indices = new HashSet<>();
            word2Vec.vocab().vocabWords().forEach(word -> {
                words.add(word.getLabel());
                indices.add(word.getIndex());
                try {
                    bw.write(word.getLabel() + "," + (word.getIndex() + 1) + "," + word.getElementFrequency() + "\n");
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
            for (int i = 0; i < word2Vec.getLayerSize(); i++) {
                bw.write("0");
                if (i < word2Vec.getLayerSize() - 1) {
                    bw.write(" ");
                }
            }
            bw.write("\n");
            double[] weights = word2Vec.getLookupTable().getWeights().data().asDouble();
            for(int i = 0; i < weights.length/word2Vec.getLayerSize(); i++) {
                double[] d = Arrays.copyOfRange(weights, i*word2Vec.getLayerSize(), i*word2Vec.getLayerSize()+word2Vec.getLayerSize());
                if(d.length!=word2Vec.getLayerSize()) throw new RuntimeException("Invalid layer size: "+d.length);
                for(int j = 0; j < d.length; j++) {
                    double x = d[j];
                    bw.write(String.valueOf(x));
                    if(j < d.length-1) {
                        bw.write(" ");
                    }
                }
                bw.write("\n");
            }
            for (int i = 0; i < word2Vec.getLayerSize(); i++) {
                bw.write("0");
                if (i < word2Vec.getLayerSize() - 1) {
                    bw.write(" ");
                }
            }
            bw.write("\n");
            bw.flush();
            bw.close();
        }
    }
}
