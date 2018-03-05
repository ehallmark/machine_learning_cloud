package graphical_modeling.model.graphs;

import graphical_modeling.model.edges.UndirectedEdge;
import graphical_modeling.model.functions.heuristic.MinimalCliqueSizeHeuristic;
import graphical_modeling.model.nodes.Node;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Evan on 4/24/2017.
 */
public class BayesianNet extends Graph {
    // directed graph

    @Override
    public void connectNodes(Node node1, Node node2) {
        if(node1==null||node2==null) return;
        node1.addChild(node2);
        node2.addParent(node1);
        node1.addNeighbor(node2);
        node2.addNeighbor(node1);
    }

    @Override
    public CliqueTree createCliqueTree() {
        MarkovNet net = this.moralize();
        net.triangulateInPlace(new MinimalCliqueSizeHeuristic());
        return net.createCliqueTree();
    }

    public MarkovNet moralize() {
        MarkovNet newNet = new MarkovNet();
        Set<UndirectedEdge> edges = new HashSet<>(); // just for keeping track
        allNodesList.forEach(node->{
            newNet.addNode(node.getLabel(),node.getCardinality());
        });
        allNodesList.forEach(node->{
            List<Node> parents = node.getInBound();
            for(int i = 0; i < parents.size(); i++) {
                // connect each parent to node
                Node parent = parents.get(i);
                newNet.connectNodes(node.getLabel(),parent.getLabel());
                edges.add(new UndirectedEdge(node,parent));
                // connect all pairs of parents
                for(int j = i+1; j < parents.size(); j++) {
                    Node parent2 = parents.get(j);
                    edges.add(new UndirectedEdge(parent,parent2));
                    newNet.connectNodes(parent.getLabel(),parent2.getLabel());
                }
            }
        });

        // factors
        factorNodes.forEach(factor->{
            newNet.addFactorNode(factor.getWeights(),newNet.findNodes(factor.getVarLabels()));
        });
        return newNet;
    }
}
