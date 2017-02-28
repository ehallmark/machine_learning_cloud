package value_estimation;

import analysis.tech_tagger.TechTagger;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 2/28/17.
 */
public class SimilarityEvaluator extends Evaluator {
    private static final String MODEL_PREFIX = "Similarity to ";
    private INDArray vector;
    private WeightLookupTable<VocabWord> lookupTable;
    public SimilarityEvaluator(String name, WeightLookupTable<VocabWord> lookupTable, INDArray avgVector) {
        super(ValueMapNormalizer.DistributionType.Normal,MODEL_PREFIX+name);
        this.vector= avgVector;
        this.lookupTable=lookupTable;
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        //throw new RuntimeException("Model does not need to be loaded...");
        return new ArrayList<>();
    }

    @Override
    public Map<String,Double> getMap() {
        throw new RuntimeException("There is no model map...");
    }

    @Override
    public double evaluate(String token) {
        double sim;
        INDArray vector2 = lookupTable.vector(token);
        if(vector2!=null&&vector!=null) {
            sim = Transforms.cosineSim(vector,vector2);
        } else sim = -1d;
        return dotProductToValue(sim);
    }


    private static double dotProductToValue(double sim) {
        double start = ValueMapNormalizer.DEFAULT_START;
        double end = ValueMapNormalizer.DEFAULT_END;
        double halfway = (end-start)/2.0;
        return start + halfway + sim*halfway;
    }
}
