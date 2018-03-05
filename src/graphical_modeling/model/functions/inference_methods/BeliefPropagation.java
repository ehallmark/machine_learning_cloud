package graphical_modeling.model.functions.inference_methods;

import graphical_modeling.model.graphs.CliqueTree;
import graphical_modeling.model.graphs.Graph;
import graphical_modeling.model.nodes.FactorNode;
import graphical_modeling.util.MathHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/13/2017.
 */
public class BeliefPropagation implements InferenceMethod {
    @Override
    public Map<String, Integer> nextAssignments(Graph graph, Map<String,Integer> currentAssignment) {
        Map<String,Integer> assignmentCopy = new HashMap<>(currentAssignment);
        List<String> nodeLabels = graph.getAllNodesList().stream().filter(node->!assignmentCopy.containsKey(node.getLabel())).map(node->node.getLabel()).collect(Collectors.toList());
        // sets weights so we can run inference
        if(!nodeLabels.isEmpty()) {
            graph.getDistributions().forEach(distribution -> distribution.updateFactorWeights());

            // Handles most cases
            CliqueTree cliqueTree = graph.createCliqueTree();
            cliqueTree.setCurrentAssignment(currentAssignment);
            Map<String, FactorNode> expectations = cliqueTree.runBeliefPropagation(nodeLabels);
            expectations.forEach((label, factor) -> {
                // Find Expectation
                double[] weights = factor.getWeights();
                int maxIdx = MathHelper.indexOfMaxValue(weights);
                if (maxIdx < 0 || maxIdx > factor.getCardinality())
                    throw new RuntimeException("Invalid assignment: " + maxIdx);
                assignmentCopy.put(label, maxIdx);
            });
        }
        return assignmentCopy;
    }
}
