package seeding;

import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import tools.VectorHelper;

import java.util.concurrent.RecursiveTask;

/**
 * Created by ehallmark on 7/21/16.
 */
public class VectorBuilderThread2D extends RecursiveTask<Double[][]> {
    private WordVectors wordVectors;
    private String text;

    public VectorBuilderThread2D(WordVectors wordVectors, String text) {
        this.wordVectors=wordVectors;
        this.text=text;
    }

    @Override
    protected Double[][] compute() {
        return VectorHelper.compute2DAvgWordVectorsFrom(wordVectors,text);
    }

}
