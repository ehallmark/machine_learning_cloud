package graphical_modeling.model.learning.algorithms;

/**
 * Created by Evan on 4/24/2017.
 */
public interface LearningAlgorithm {
    // returns convergence
    boolean runAlgorithm();
    double computeCurrentScore();
}
