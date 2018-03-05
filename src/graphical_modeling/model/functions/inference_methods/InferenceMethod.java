package graphical_modeling.model.functions.inference_methods;

import graphical_modeling.model.graphs.Graph;

import java.util.Map;

/**
 * Created by Evan on 5/13/2017.
 */
public interface InferenceMethod {
    Map<String,Integer> nextAssignments(Graph graph, Map<String, Integer> currentAssignment);
}
