package graphical_modeling.model.graphs;

import graphical_modeling.model.edges.Edge;
import graphical_modeling.model.edges.UndirectedEdge;
import graphical_modeling.model.functions.heuristic.TriangulationHeuristic;
import graphical_modeling.model.functions.normalization.DivideByPartition;
import graphical_modeling.model.nodes.CliqueNode;
import graphical_modeling.model.nodes.Node;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Created by Evan on 4/24/2017.
 */
public class MarkovNet extends Graph {
    // undirected graph
    public MarkovNet() {
        super();
    }

    public void connectNodes(Node node1, Node node2) {
        if(node1==null||node2==null) return;
        node1.addNeighbor(node2);
        node2.addNeighbor(node1);
    }

    // Returns triangulated (chordal) version of this graph
    //  based on given triangulation heuristic
    public void triangulateInPlace(TriangulationHeuristic heuristic) {
        List<Node> copyOfNodes = new ArrayList<>(allNodesList);
        Set<Edge<Node>> edges = new HashSet<>(allNodesList.size());
        Function<List<Node>,Integer> function = heuristic.nextNodeToEliminateFunction();
        while(!copyOfNodes.isEmpty()) {
            Integer nodeIdx = function.apply(copyOfNodes);
            Node node = copyOfNodes.get(nodeIdx);
            // add links to pairs adjacent nodes and store in edges Set
            List<Node> neighbors = node.getNeighbors();
            for(int i = 0; i < neighbors.size(); i++) {
                // add edge for easy reconstruction
                Node n1 = neighbors.get(i);
                edges.add(new UndirectedEdge<>(node,n1));
                for(int j = i+1; j < neighbors.size(); j++) {
                    Node n2 = neighbors.get(j);
                    connectNodes(n1,n2);
                    edges.add(new UndirectedEdge<>(n1,n2));
                }
            }
            // remove node and all links to other nodes
            copyOfNodes.remove(nodeIdx.intValue());
            // this method removes the correct connections
            node.removeNeighborConnections();
        }

        // reconstruct
        edges.forEach(edge->{
            connectNodes(edge.getNode1(),edge.getNode2());
        });
    }

    public CliqueTree createCliqueTree() {
        return createCliqueTree(this);
    }

    public static CliqueTree createCliqueTree(MarkovNet graph) {
        // maximum cardinality search using perfect ordering
        List<Node> PEO = graph.findPerfectEliminitationOrdering();
        // Reverse Perfect Elimination Algorithm
        Collections.reverse(PEO);

        // find maximal clique tree
        AtomicInteger prevMark = new AtomicInteger(-1);
        AtomicInteger j = new AtomicInteger(0);
        Map<Node,Integer> markMap = new HashMap<>();
        Map<Node,Set<Node>> M = new HashMap<>();
        Map<Node,CliqueNode> C = new HashMap<>();
        Map<Node,Node> lastMap = new HashMap<>();
        AtomicReference<CliqueNode> CjRef = new AtomicReference<>(new CliqueNode());
        graph.allNodesList.forEach(node->{
            markMap.put(node,0);
            M.put(node,new HashSet<>());
        });
        CliqueTree cliqueTree = new CliqueTree(graph);
        cliqueTree.addNode(CjRef.get());


        PEO.forEach(node->{
            CliqueNode Cj = CjRef.get();
            int markX = markMap.get(node);
            if(markX<=prevMark.get()) {
                j.getAndIncrement();
                // create clique
                Cj = new CliqueNode(new ArrayList<>(M.get(node)));
                CjRef.set(Cj);
                Cj.addNode(node);
                // add node to graph
                cliqueTree.addNode(Cj);
                // create link
                Node lastNode = lastMap.get(node);
                cliqueTree.connectNodes(C.get(lastNode),Cj);
            } else {
                Cj.addNode(node);
            }

            node.getNeighbors().forEach(neighbor->{
                M.get(neighbor).add(node);
                markMap.put(neighbor,markMap.get(neighbor)+1);
                lastMap.put(neighbor,node);
            });

            prevMark.set(markX);
            C.put(node,Cj);
        });

        // Build the factors
        cliqueTree.factorNodes=new ArrayList<>(graph.factorNodes);
        cliqueTree.constructFactors();

        // Re-Normalize Values to Probabilities
        cliqueTree.reNormalize(new DivideByPartition());
        return cliqueTree;
    }

    // Make sure the graph is triangulated, or one may not exist!
    public List<Node> findPerfectEliminitationOrdering() {
        // sequence of sigmas
        List<Set<Node>> setSequence = new LinkedList<>();
        // to output
        List<Node> outputSequence = new LinkedList<>();
        // initial set of sequence
        setSequence.add(new HashSet<>(allNodesList));
        while(!setSequence.isEmpty()) {
            // find and remove a node from the set sequence
            Set<Node> firstSet = setSequence.get(0);
            Node node = firstSet.stream().findAny().get();
            firstSet.remove(node);

            // if first set of sequence is empty, remove it
            if(firstSet.isEmpty()) setSequence.remove(0);

            // add node to the end of output
            outputSequence.add(0, node);

            // for each neighbor of node that exists in one of the sequence sets S
            List<Node> remainingNeighbors = new LinkedList<>(node.getNeighbors());

            int s = 0;
            while(s<setSequence.size()) {
                Set<Node> set = setSequence.get(s);
                Set<Node> newSet = new HashSet<>();
                int i = 0;
                while(i < remainingNeighbors.size()) {
                    Node neighbor = remainingNeighbors.get(i);
                    if(set.contains(neighbor)) {
                        // if set S has not been replaced while processing this node
                        newSet.add(neighbor);
                        set.remove(neighbor);
                        if(set.isEmpty()) {
                            setSequence.remove(s);
                            break;
                        }
                        // move w from S to T
                        remainingNeighbors.remove(i);
                    } else {
                        i++;
                    }

                }
                if(!newSet.isEmpty()) {
                    // create new empty replacement set T and place it before S in the sequence
                    setSequence.add(s,newSet);
                    s++;
                }
                s++;
            }
        }
        return outputSequence;
    }


}
