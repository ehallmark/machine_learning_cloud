package graphical_modeling.model.functions.inference_methods;

import graphical_modeling.model.graphs.GibbsChain;
import graphical_modeling.model.graphs.Graph;
import graphical_modeling.model.graphs.MetropolisHastingsChain;
import graphical_modeling.model.nodes.FactorNode;
import graphical_modeling.util.MathHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/13/2017.
 */
public class SamplingMethod implements InferenceMethod {
    protected Type type;
    protected int burnIn;
    public enum Type { Gibbs, MetropolisHastings }

    public SamplingMethod(int burnIn, Type type) {
        this.type=type;
        this.burnIn=burnIn;
    }

    @Override
    public Map<String, Integer> nextAssignments(Graph graph, Map<String,Integer> currentAssignment) {
        Map<String,Integer> assignmentCopy = new HashMap<>(currentAssignment);
        List<String> nodeLabels = graph.getAllNodesList().stream().filter(node->!assignmentCopy.containsKey(node.getLabel())).map(node->node.getLabel()).collect(Collectors.toList());
        // sets weights so we can run inference
        if(!nodeLabels.isEmpty()) {
            graph.getDistributions().forEach(distribution -> distribution.updateFactorWeights());

            Iterator<Map<String,FactorNode>> chain;
            switch(type) {
                case Gibbs: {
                    chain = new GibbsChain(graph,currentAssignment);
                    break;
                } case MetropolisHastings: {
                    chain = new MetropolisHastingsChain(graph,currentAssignment);
                    break;
                }default: {
                    throw new RuntimeException("Unknown sampling type");
                }
            }

            for(int i = 0; i < burnIn && chain.hasNext(); i++) chain.next();

            Map<String, FactorNode> expectations = chain.next();
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
