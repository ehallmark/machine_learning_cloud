package ui_models.attributes.classification;

import org.deeplearning4j.berkeley.Pair;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 5/9/2017.
 */
public interface ClassificationAttr extends AbstractAttribute<List<Pair<String,Double>>> {
    void train(Map<String,Collection<String>> trainingData);
    void optimizeHyperParameters(Map<String,Collection<String>> trainingData, Map<String,Collection<String>> validationData);
    int numClassifications();
    Collection<String> getClassifications();
}
