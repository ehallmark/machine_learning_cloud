package graphical_modeling.model.graphs;

import graphical_modeling.model.functions.normalization.DivideByPartition;
import graphical_modeling.model.nodes.FactorNode;
import lombok.Setter;

import java.util.*;

/**
 * Created by Evan on 5/12/2017.
 */
public class MetropolisHastingsChain implements Iterator<Map<String,FactorNode>> {
    protected Graph graph;
    protected Map<String,Integer> currentAssignments;
    @Setter
    protected Map<String,Integer> permanentAssignments;
    protected Random rand = new Random(69);
    public MetropolisHastingsChain(Graph graph, Map<String,Integer> permanentAssignments) {
        this.graph=graph;
        this.permanentAssignments=permanentAssignments;
        this.currentAssignments=new HashMap<>();
        this.initCurrentAssignments();

    }

    private void initCurrentAssignments() {
        graph.allNodesList.forEach(node->{
            currentAssignments.put(node.getLabel(),rand.nextInt(node.getCardinality()));
        });
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Map<String, FactorNode> next() {
        return nextProbability();
    }

    private Map<String,FactorNode> nextProbability() {
        Map<String,FactorNode> results = new HashMap<>();
        graph.allNodesList.forEach(node->{
            if(permanentAssignments.containsKey(node.getLabel())) {
                int fixedAssignment = permanentAssignments.get(node.getLabel());
                results.put(node.getLabel(), Graph.givenValueFactor(node,fixedAssignment));
                currentAssignments.put(node.getLabel(),fixedAssignment);
            } else {
                double u = rand.nextDouble();
                // generate x(t+1) from x(t)
                int nextAssignment = rand.nextInt(node.getCardinality()-1);
                int currAssignment = currentAssignments.get(node.getLabel());
                if(nextAssignment==currAssignment)nextAssignment++; // prevent duplicate

                // sum-product out other variables
                Collection<FactorNode> factors = new ArrayList<>(node.getFactors());
                Set<String> scope = new HashSet<>();
                factors.forEach(mem -> scope.addAll(mem.getVarToIndexMap().keySet()));
                scope.remove(node.getLabel());
                FactorNode result = factors.parallelStream().reduce((f1, f2) -> f1.multiply(f2)).get();
                if (result.getNumVariables() > 1) {
                    Set<String> toSumOut = new HashSet<>(Arrays.asList(result.getVarLabels()));
                    toSumOut.remove(node.getLabel());
                    result = result.sumOut(toSumOut.toArray(new String[toSumOut.size()]));
                }
                result.reNormalize(new DivideByPartition());

                double probProposal = result.getWeights()[nextAssignment];
                double probCurrentState = result.getWeights()[currAssignment];

                if(probCurrentState==0||u<(probProposal/probCurrentState)) {
                    currentAssignments.put(node.getLabel(), nextAssignment);
                }
                // add results
                results.put(node.getLabel(), result);
            }
        });
        return results;
    }
}
