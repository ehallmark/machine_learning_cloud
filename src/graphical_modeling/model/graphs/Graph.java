package graphical_modeling.model.graphs;

import graphical_modeling.model.functions.normalization.DivideByPartition;
import graphical_modeling.model.functions.normalization.NormalizationFunction;
import graphical_modeling.model.learning.algorithms.LearningAlgorithm;
import graphical_modeling.model.learning.distributions.Distribution;
import graphical_modeling.model.nodes.FactorNode;
import graphical_modeling.model.nodes.Node;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by Evan on 4/13/2017.
 */
public abstract class Graph implements Serializable {
    private static final long serialVersionUID = 68l;
    @Getter
    @Setter
    protected List<Distribution> distributions;
    protected Map<String, Node> labelToNodeMap;
    @Getter
    protected List<FactorNode> factorNodes;
    @Getter
    protected List<Node> allNodesList;
    @Getter
    @Setter
    protected Map<String,Integer> currentAssignment;
    @Getter
    @Setter
    protected Collection<Map<String,Integer>> trainingData;
    @Getter
    @Setter
    protected Collection<Map<String,Integer>> validationData;
    @Getter
    @Setter
    protected Collection<Map<String,Integer>> testData;

    public Graph() {
        this.labelToNodeMap=new HashMap<>();
        this.allNodesList=new ArrayList<>();
        this.factorNodes=new ArrayList<>();
    }

    public abstract CliqueTree createCliqueTree();

    public Node addBinaryNode(String label) { // default binary
        return this.addNode(label,2);
    }

    public Node findNode(String label) {
        return labelToNodeMap.get(label);
    }

    public Node addNode(String label, int cardinality) {
        if(labelToNodeMap.containsKey(label)) return labelToNodeMap.get(label);
        Node node = new Node(label,cardinality);
        allNodesList.add(node);
        labelToNodeMap.put(label, node);
        return node;
    }

    public void reNormalize(NormalizationFunction function) {
        factorNodes.forEach(node->{
            node.reNormalize(function);
        });
    }

    public FactorNode addFactorNode(double[] weights, Node... connectingNodes) {
        String[] connectingLabels = new String[connectingNodes.length];
        int[] varCardinalities = new int[connectingNodes.length];
        for(int i = 0; i < connectingNodes.length; i++) {
            Node node = connectingNodes[i];
            String label = node.getLabel();
            if(label==null) throw new RuntimeException("Unable to find node");
            connectingLabels[i] = label;
            varCardinalities[i] = node.getCardinality();
        }
        FactorNode factor = new FactorNode(weights,connectingLabels,varCardinalities);
        for(int i = 0; i < connectingNodes.length; i++) {
            Node node = connectingNodes[i];
            node.addFactor(factor);
        }
        factorNodes.add(factor);
        return factor;
    }

    public Node[] findNodes(String... labels) {
        Node[] toReturn = new Node[labels.length];
        for(int i = 0; i < toReturn.length; i++) {
            toReturn[i]=findNode(labels[i]);
        }
        return toReturn;
    }

    public void applyLearningAlgorithm(LearningAlgorithm function, int epochs) {
        for(int epoch = 0; epoch < epochs; epoch++) {
            System.out.println("Starting epoch: "+(epoch+1));
            boolean converged = function.runAlgorithm();
            Double currentScore = function.computeCurrentScore();
            System.out.println("    Score: "+currentScore);
            if(converged) {
                System.out.println("Converged during epoch: "+(epoch+1));
                break;
            }
        }
    }

    public void connectNodes(String label1, String label2) {
        connectNodes(labelToNodeMap.get(label1),labelToNodeMap.get(label2));
    }

    public abstract void connectNodes(Node node1, Node node2);

    public FactorNode variableElimination(String[] queryVars) {
        if(queryVars==null||queryVars.length==0) throw new RuntimeException("Must set queryVars");

        Set<String> queryLabels = new HashSet<>();
        Arrays.stream(queryVars).forEach(var->queryLabels.add(var));

        // choose elimination ordering

        // Initialize F
        AtomicReference<List<FactorNode>> F = new AtomicReference<>(new LinkedList<>());
        factorNodes.forEach(fac->{
            F.get().add(fac);
        });

        if(currentAssignment!=null) {
            // add in evidence
            currentAssignment.forEach((label, value) -> {
                Node x = labelToNodeMap.get(label);
                F.get().add(givenValueFactor(x, value));
            });
        }

        for(Node z : allNodesList) {
            if(!queryLabels.contains(z.getLabel())) {
                F.set(sumProductVariableElimination(F.get(), z));
            }
        }

        if(F.get().isEmpty()) throw new RuntimeException("Factor Stack should not be empty");

        // multiply to get query
        FactorNode query = F.get().remove(0);
        while(!F.get().isEmpty()) query = query.multiply(F.get().remove(0));

        query.reNormalize(new DivideByPartition());
        return query;
    }

    public static FactorNode givenValueFactor(Node node, int val) {
        double[] weights = new double[node.getCardinality()];
        Arrays.fill(weights,0d);
        weights[val] = 1d;
        return new FactorNode(weights,new String[]{node.getLabel()},new int[]{node.getCardinality()});
    }

    protected List<FactorNode> sumProductVariableElimination(List<FactorNode> F, Node x) {
        FactorNode newFac = null;
        List<FactorNode> newList = new LinkedList<>();
        while(!F.isEmpty()) {
            FactorNode f = F.remove(0);
            if(f.getVarToIndexMap().containsKey(x.getLabel())) {
                if(newFac==null) newFac=f;
                else newFac=newFac.multiply(f);
            } else {
                newList.add(f);
            }
        }
        if(newFac==null) throw new RuntimeException("Nothing happened");
        // sum out
        newFac=newFac.sumOut(new String[]{x.getLabel()});
        newList.add(newFac);
        return newList;
    }
}
