package graphical_modeling.model.graphs;

import graphical_modeling.model.functions.normalization.DivideByPartition;
import graphical_modeling.model.nodes.FactorNode;

import java.util.*;

/**
 * Created by Evan on 5/12/2017.
 */
public class GibbsChain implements Iterator<Map<String,FactorNode>> {
    protected Graph graph;
    protected Map<String,Integer> currentAssignments;
    protected final Map<String,Integer> permanentAssignments;
    protected Random rand = new Random(69);
    public GibbsChain(Graph graph, Map<String,Integer> permanentAssignments) {
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
                Collection<FactorNode> factors = new ArrayList<>(node.getFactors());
                Set<String> scope = new HashSet<>();
                factors.forEach(mem -> scope.addAll(mem.getVarToIndexMap().keySet()));
                scope.remove(node.getLabel());
                // incorporate assignments into factor
                scope.forEach(mem -> factors.add(Graph.givenValueFactor(graph.findNode(mem), permanentAssignments.containsKey(mem) ? permanentAssignments.get(mem) : currentAssignments.get(mem))));
                FactorNode result = factors.parallelStream().reduce((f1, f2) -> f1.multiply(f2)).get();
                if (result.getNumVariables() > 1) {
                    Set<String> toSumOut = new HashSet<>(Arrays.asList(result.getVarLabels()));
                    toSumOut.remove(node.getLabel());
                    result = result.sumOut(toSumOut.toArray(new String[toSumOut.size()]));
                }
                result.reNormalize(new DivideByPartition());
                // add results
                results.put(node.getLabel(), result);
                // update current assignment map
                int nextAssignment = result.nextSample();
                currentAssignments.put(node.getLabel(), nextAssignment);
            }
        });
        return results;
    }
}
