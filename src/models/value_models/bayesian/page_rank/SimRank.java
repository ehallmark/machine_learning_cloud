package models.value_models.bayesian.page_rank;

import com.google.common.util.concurrent.AtomicDouble;
import model.edges.Edge;
import model.edges.UndirectedEdge;
import model.graphs.BayesianNet;
import model.learning.algorithms.LearningAlgorithm;
import model.nodes.Node;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/20/17.
 */
public class SimRank extends RankGraph<Edge<String>> {
    private final int jaccardDepth = 3;
    private static double currentScore = Double.MAX_VALUE;

    public SimRank(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> importantLabels, double damping) {
        super(labelToCitationLabelsMap,importantLabels,damping);
    }

    protected void initGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> importantLabels) {
        rankTable=new HashMap<>(labelToCitationLabelsMap.size());
        System.out.println("Adding initial nodes...");
        labelToCitationLabelsMap.forEach((label,citations)->{
            graph.addBinaryNode(label);
            citations.forEach(citation->{
                graph.addBinaryNode(citation);
                graph.connectNodes(label, citation);
            });
        });
        this.nodes=graph.getAllNodesList();
        AtomicInteger cnt = new AtomicInteger(0);
        (importantLabels!=null?importantLabels.stream().map(label->graph.findNode(label)):this.nodes.stream()).forEach(node->{
            if(node!=null) {
                if (cnt.getAndIncrement() % 10000 == 0) {
                    System.out.println("Added neighbors of " + cnt.get() + " patents so far");
                }
                addNeighborsToMap(node, node, 0, jaccardDepth);
            }
        });
        System.out.println("Done.");
    }

    protected void addNeighborsToMap(Node thisNode, Node otherNode, int currentIdx, int maxIdx) {
        Edge<String> edge = new UndirectedEdge<>(thisNode.getLabel(),otherNode.getLabel());
        rankTable.put(edge,thisNode.getLabel().equals(otherNode.getLabel())?1f:0f);
        if(currentIdx<maxIdx) {
            Collection<Node> neighbors;
            if(graph instanceof BayesianNet) {
                neighbors=otherNode.getInBound();
            } else {
                neighbors=otherNode.getNeighbors();
            }
            neighbors.forEach(neighbor -> {
                addNeighborsToMap(thisNode, neighbor, currentIdx + 1, maxIdx);
            });
        }
    }

    @Override
    public LearningAlgorithm getLearningAlgorithm() {
        return new Algorithm();
    }


    protected double rankValue(Node n1, Node n2) {
        if(n1.equals(n2)) return 1d;
        Collection<Node> neighbors1;
        Collection<Node> neighbors2;
        if(graph instanceof BayesianNet) {
            neighbors1=n1.getInBound();
            neighbors2=n2.getInBound();
        } else {
            neighbors1=n1.getNeighbors();
            neighbors2=n2.getNeighbors();
        }
        if(neighbors1.size()==0||neighbors2.size()==0) return 0d;
        return (damping / (neighbors1.size()*neighbors2.size())) *
                neighbors1.stream().collect(Collectors
                        .summingDouble(fam1->neighbors2.stream().collect(Collectors.summingDouble(fam2->{
                            Float famRank = rankTable.get(new UndirectedEdge<>(fam1.getLabel(),fam2.getLabel()));
                            if(famRank==null) return 0f;
                            else return famRank;
                        }))));
    }

    public class Algorithm implements LearningAlgorithm {
        @Override
        public boolean runAlgorithm() {
            Collection<Edge<String>> rankTableKeysCopy = new HashSet<>(rankTable.keySet());
            AtomicDouble delta = new AtomicDouble(0d);
            AtomicInteger deltaCount = new AtomicInteger(0);
            rankTableKeysCopy.parallelStream().forEach(edge->{
                if(!edge.getNode1().equals(edge.getNode2())) {
                    Node n1 = graph.findNode(edge.getNode1());
                    Node n2 = graph.findNode(edge.getNode2());
                    double newRank = rankValue(n1, n2);
                    //System.out.println("Rank ["+n1.getLabel()+","+n2.getLabel()+"]: "+newRank);
                    if(rankTable.containsKey(edge)) {
                        double prevRank = rankTable.get(edge);
                        delta.getAndAdd(Math.abs(prevRank-newRank));
                        deltaCount.getAndIncrement();
                    }
                    if (newRank > 0) {
                        rankTable.put(edge, (float) newRank);
                    }
                }
            });
            if(deltaCount.get()>0)currentScore = delta.get()/deltaCount.get();
            return currentScore  < 0.0000001/nodes.size();
        }

        @Override
        public double computeCurrentScore() {
            return currentScore;
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String,Collection<String>> test = new HashMap<>();
        test.put("n1",Arrays.asList("n2","n3"));
        test.put("n2",Arrays.asList("n1"));
        test.put("n3",Collections.emptyList());
        test.put("n4",Arrays.asList("n1","n2"));
        double damping = 0.75;
        SimRank pr = new SimRank(test,null,damping);
        pr.solve(10);
        //System.out.println("Similar to n4: "+String.join("; ",pr.findSimilarDocuments(Arrays.asList("n4"),3,4,2)));
    }

}
