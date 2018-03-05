package graphical_modeling.model.learning.distributions;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by ehallmark on 4/28/17.
 */
public interface Distribution extends Serializable {
    void train(Map<String, Integer> assignmentMap);
    void initialize();
    void updateFactorWeights();
    boolean getConverged();
    double getScore();
}
