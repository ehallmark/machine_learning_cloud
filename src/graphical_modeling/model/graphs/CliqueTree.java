package graphical_modeling.model.graphs;

import graphical_modeling.model.functions.normalization.DivideByPartition;
import graphical_modeling.model.nodes.CliqueNode;
import graphical_modeling.model.nodes.FactorNode;
import graphical_modeling.model.nodes.Node;
import graphical_modeling.util.CliqueFactorList;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 4/25/2017.
 */
public class CliqueTree extends BayesianNet {
    protected Graph originalGraph;
    public CliqueTree(Graph originalGraph) {
        super();
        this.originalGraph=originalGraph;
    }

    public CliqueNode addNode(CliqueNode node) {
        allNodesList.add(node);
        return node;
    }

    @Override
    public Node addNode(String node, int cardinality) {
        throw new UnsupportedOperationException("Must use addNode(CliqueNode) signature");
    }

    @Override
    public CliqueTree createCliqueTree() {
        return this;
    }

    public void constructFactors() {
        allNodesList.forEach(node->{
            if(node instanceof CliqueNode) {
                CliqueNode clique = (CliqueNode)node;
                FactorNode endFactor = null;
                int i = 0;
                while(i < factorNodes.size()) {
                    FactorNode factor = factorNodes.get(i);
                    // find clique to assign it to
                    if(clique.hasFactorScope(factor.getVarLabels())) {
                        if(endFactor==null) endFactor=factor;
                        else endFactor= endFactor.multiply(factor);
                        factorNodes.remove(i);
                    } else i++;
                }
                if(endFactor==null) throw new RuntimeException("Node "+node.getLabel()+" has no factor");
                clique.setCliqueFactor(endFactor);
            }
        });
        if(factorNodes.size()>0) {
            for(FactorNode factor : factorNodes) {
                CliqueNode cliqueNode = new CliqueNode(factor.getNeighbors());
                cliqueNode.setCliqueFactor(factor);
                allNodesList.add(cliqueNode);
            }
            //throw new RuntimeException("Could not include every factor!");
            System.out.println("WARNING: Could not include every factor!");
        }
        this.factorNodes=new CliqueFactorList(allNodesList);
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("Num nodes: "+allNodesList.size());
        AtomicInteger idx = new AtomicInteger(0);
        allNodesList.forEach(n->{
            CliqueNode clique = (CliqueNode)n;
            sj.add("Clique: "+idx.getAndIncrement());
            sj.add(clique.getCliqueFactor().toString());
        });
        return sj.toString();
    }

    // Recursive
    protected void accumulateMessagesTo(CliqueNode root) {
        // head recursion
        root.getChildren().forEach(child->{
            accumulateMessagesTo((CliqueNode)child);
        });

        // incorporate any messages
        root.incorporateMessagesIntoFactor();
        // send next messages
        root.prepAndSendMessageToParent();
    }

    // Recursive
    protected void propagateMessagesFrom(CliqueNode root) {
        // incorporate any messages
        root.incorporateMessagesIntoFactor();

        // send next messages
        root.prepAndSendMessagesToChildren();

        // tail recursion
        root.getChildren().forEach(child->{
            propagateMessagesFrom((CliqueNode)child);
        });
    }

    // returns marginals for each variable
    public Map<String,FactorNode> runBeliefPropagation(Collection<String> toQuery) {
        // select root (could be a forest)
        List<Node> roots = allNodesList.stream().filter(n->n.getInBound().isEmpty()).collect(Collectors.toList());
        // add assignments
        if(currentAssignment!=null) {
            allNodesList.forEach(node->{
                CliqueNode cliqueNode = (CliqueNode)node;
                // add in evidence
                currentAssignment.forEach((label, value) -> {
                    if(cliqueNode.hasFactorScope(new String[]{label})) {
                        Node x = originalGraph.findNode(label);
                        cliqueNode.setCliqueFactor(cliqueNode.getCliqueFactor().multiply(givenValueFactor(x, value)));
                    }
                });
            });
        }

        if(roots.isEmpty()) {
            throw new RuntimeException("No root found");
        }

        roots.forEach(root->{
            // 1) pass messages inwards starting from the leaves
            accumulateMessagesTo((CliqueNode)root);

            // 2) second message passing starting from root
            propagateMessagesFrom((CliqueNode)root);
        });

        Map<String,List<CliqueNode>> scopeMap = new HashMap<>();
        // 3) extract marginals for each variable
        toQuery.forEach(nodeLabel->{
            allNodesList.forEach(_cnode->{
                CliqueNode cliqueNode = (CliqueNode)_cnode;
                if(cliqueNode.hasFactorScope(new String[]{nodeLabel})) {
                    if(scopeMap.containsKey(nodeLabel)) {
                        scopeMap.get(nodeLabel).add(cliqueNode);
                    } else {
                        List<CliqueNode> list = new ArrayList<>();
                        list.add(cliqueNode);
                        scopeMap.put(nodeLabel,list);
                    }
                }
            });
        });

        Map<String,FactorNode> toReturn = new HashMap<>();
        scopeMap.forEach((nodeLabel,cliques)->{
            FactorNode factor = cliques.stream().map(clique->clique.getCliqueFactor()).reduce((f1,f2)->f1.multiply(f2)).get();
            Set<String> labelsToSumOver = new HashSet<>(Arrays.asList(factor.getVarLabels()));
            labelsToSumOver.remove(nodeLabel);
            FactorNode result = factor.sumOut(labelsToSumOver.toArray(new String[labelsToSumOver.size()]));
            result.reNormalize(new DivideByPartition());
            toReturn.put(nodeLabel,result);
        });
        return toReturn;
    }
}
