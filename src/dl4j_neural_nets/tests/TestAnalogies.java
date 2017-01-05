package dl4j_neural_nets.tests;

import dl4j_neural_nets.vectorization.WordVectorModel;
import edu.stanford.nlp.util.Triple;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/5/16.
 */
public class TestAnalogies {
    private WordVectors model;
    private List<Triple<String,String,String>> analogies;
    private int numPredictions;

    public TestAnalogies(WordVectors model, List<Triple<String,String,String>> analogies, int numPredictions) {
        this.model=model;
        this.analogies=analogies;
        this.numPredictions=numPredictions;
    }

    public void printAnalogies() {
        analogies.forEach(analogy->{
            INDArray v1 = model.getWordVectorMatrix(analogy.first);
            if(v1==null)return;
            INDArray v2 = model.getWordVectorMatrix(analogy.second);
            if(v2==null) return;
            INDArray v3 = model.getWordVectorMatrix(analogy.third);
            if(v3==null)return;
            INDArray similarTo = v3.add(v2.sub(v1));

            List<String> similar = model.wordsNearest(similarTo,numPredictions+3).stream()
                    .filter(w->!(w.equals(analogy.first)||w.equals(analogy.second)||w.equals(analogy.third)))
                    .limit(numPredictions)
                    .collect(Collectors.toList());
            System.out.println(analogy.first+" is to "+analogy.second+" as "+analogy.third+" is to: ");
            System.out.println(String.join(", ",similar));
        });
    }

    public static void main(String[] args) throws IOException{
        List<Triple<String,String,String>> analogies = new ArrayList<>();
        analogies.add(new Triple<>("man","king","woman"));
        analogies.add(new Triple<>("france","paris","germany"));
        analogies.add(new Triple<>("iphone","apple","android"));
        analogies.add(new Triple<>("xbox","microsoft","playstation"));
        analogies.add(new Triple<>("internet","computer","radio"));
        analogies.add(new Triple<>("electricity","capacitor","bytes"));
        analogies.add(new Triple<>("electricity","battery","bytes"));
        WordVectors model = WordVectorSerializer.loadGoogleModel(new File("GoogleNews-vectors-negative300.bin"),true);
        TestAnalogies test = new TestAnalogies(model,analogies,10);
        System.out.println("Old Model: ");
        test.printAnalogies();
        model = null;
        System.gc();
        System.gc();
        System.gc();
        int epoch = 0;
        WordVectors model2 = WordVectorModel.load(WordVectorModel.wordVectorFile.getAbsolutePath()+epoch);
        TestAnalogies test2 = new TestAnalogies(model2,analogies,10);
        System.out.println("New Model: ");
        test2.printAnalogies();
    }
}
