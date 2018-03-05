package graphical_modeling.model.learning.algorithms;

import graphical_modeling.model.graphs.Graph;
import graphical_modeling.model.learning.distributions.DirichletCreator;
import graphical_modeling.model.learning.distributions.DistributionCreator;

import java.util.Map;

/**
 * Created by Evan on 4/29/2017.
 */
public class BayesianLearningAlgorithm extends AbstractLearningAlgorithm {
    public BayesianLearningAlgorithm(Graph graph, double alpha) {
        this(graph,new DirichletCreator(alpha));
    }

    protected BayesianLearningAlgorithm(Graph graph, DistributionCreator creator) {
        super(creator,graph);
    }

    protected Map<String,Integer> handleAssignment(Map<String,Integer> assignment, Graph graph) {
        return assignment;
    }

}
