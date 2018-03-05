package graphical_modeling.model.learning.algorithms;

import graphical_modeling.model.functions.inference_methods.InferenceMethod;
import graphical_modeling.model.graphs.Graph;

import java.util.Map;

/**
 * Created by ehallmark on 5/10/17.
 */
public class ExpectationMaximizationAlgorithm extends BayesianLearningAlgorithm {
    protected InferenceMethod inferenceMethod;
    public ExpectationMaximizationAlgorithm(Graph graph, double alpha, InferenceMethod inferenceMethod) {
        super(graph,alpha);
        this.inferenceMethod=inferenceMethod;
    }

    @Override
    protected Map<String,Integer> handleAssignment(Map<String,Integer> assignment, Graph graph) {
        return inferenceMethod.nextAssignments(graph,assignment);
    }
}
