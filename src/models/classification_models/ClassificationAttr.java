package models.classification_models;

import org.nd4j.linalg.primitives.PairBackup;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ClassificationAttr {
    public abstract void train(Map<String,Collection<String>> trainingData);
    public abstract ClassificationAttr optimizeHyperParameters(Map<String,Collection<String>> trainingData, Map<String,Collection<String>> validationData);
    public abstract int numClassifications();
    public abstract Collection<String> getClassifications();
    public abstract void save();
    public abstract ClassificationAttr untrainedDuplicate();
    public String getType() {
        return "keyword";
    }
    public abstract List<PairBackup<String,Double>> attributesFor(Collection<String> portfolio, int limit);

}
