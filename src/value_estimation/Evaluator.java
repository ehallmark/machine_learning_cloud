package value_estimation;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import seeding.Database;

import java.util.Collections;
import java.util.Map;

/**
 * Created by Evan on 1/27/2017.
 */
public abstract class Evaluator {
    // Instance class
    protected Map<String,Double> model;
    protected WeightLookupTable<VocabWord> lookupTable;
    public Evaluator(WeightLookupTable<VocabWord> lookupTable) {
        this.lookupTable=lookupTable;
        this.model=loadModel();
        ValueMapNormalizer.normalizeToRange(model,1.0,5.0);
    }

    protected abstract Map<String,Double> loadModel();

    // Returns value between 1 and 5
    public double evaluate(String token) {
        if(model.containsKey(token)) {
            return model.get(token);
        } else {
            return 1.0;
        }
    }
}
