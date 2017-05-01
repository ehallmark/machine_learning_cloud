package citation_graph.page_rank;

import model.edges.Edge;
import model.edges.UndirectedEdge;
import model.graphs.Graph;
import model.learning.algorithms.LearningAlgorithm;
import model.nodes.Node;
import util.ObjectIO;
import util.Pair;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/20/17.
 */
public class SimRank extends RankGraph<Edge> {
    private static final int jaccardDepth = 2;
    public SimRank(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, double damping) {
        super(labelToCitationLabelsMap, damping);
    }

    protected void initGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap) {
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
        labelToCitationLabelsMap.keySet().parallelStream().forEach(nodeLabel->{
            System.out.println("Adding neighbors of: "+cnt.getAndIncrement());
            Node node = graph.findNode(nodeLabel);
           addNeighborsToMap(node,node,0,jaccardDepth);
        });
        System.out.println("Done.");
    }

    protected void addNeighborsToMap(Node thisNode, Node otherNode, int currentIdx, int maxIdx) {
        rankTable.put(new UndirectedEdge(thisNode,otherNode),thisNode.getLabel().equals(otherNode.getLabel())?1f:0f);
        if(currentIdx<maxIdx) {
            otherNode.getInBound().forEach(neighbor->{
                System.out.print("-");
                addNeighborsToMap(thisNode,neighbor,currentIdx+1,maxIdx);
            });
            System.out.println();
        }
    }

    @Override
    public LearningAlgorithm getLearningAlgorithm() {
        return new Algorithm();
    }

    public List<Pair<String,Float>> findSimilarDocuments(Collection<String> nodeLabels, int limit) {
        return findSimilarDocumentsFromRankTable(rankTable,nodeLabels,limit);
    }

    public static List<Pair<String,Float>> findSimilarDocumentsFromRankTable(Map<Edge,Float> rankTable, Collection<String> nodeLabels, int limit) {
        // greedily iterate through all values and sum ranks over nodelabels
        Map<String,Float> scoreMap = new HashMap<>();
        rankTable.entrySet().stream().filter(e->{
            Edge edge = e.getKey();
            if(nodeLabels.contains(edge.getNode1().getLabel())||nodeLabels.contains(edge.getNode2().getLabel())) {
                return true;
            }
            return false;
        }).forEach(e->{
            Edge edge = e.getKey();
            scoreMap.put(edge.getNode1().getLabel(),e.getValue());
            scoreMap.put(edge.getNode2().getLabel(),e.getValue());
        });
        return scoreMap.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(limit).map(e->new Pair<>(e.getKey(),e.getValue())).collect(Collectors.toList());
    }

    protected double rankValue(Node n1, Node n2) {
        if(n1.equals(n2)) return 1d;
        if(n1.getInBound().size()==0||n2.getInBound().size()==0) return 0d;
        return (damping / (n1.getInBound().size()*n2.getInBound().size())) *
                n1.getInBound().stream().collect(Collectors
                        .summingDouble(fam1->n2.getInBound().stream().collect(Collectors.summingDouble(fam2->{
                            Float famRank = rankTable.get(new UndirectedEdge(fam1,fam2));
                            if(famRank==null) return 0f;
                            else return famRank;
                        }))));
    }

    public class Algorithm implements LearningAlgorithm {
        @Override
        public Function<Graph, Void> runAlgorithm() {
            return (graph) -> {
                AtomicInteger cnt = new AtomicInteger(0);
                Collection<Edge> rankTableKeysCopy = new HashSet<>(rankTable.keySet());
                rankTableKeysCopy.parallelStream().forEach(edge->{
                    Node n1 = edge.getNode1();
                    Node n2 = edge.getNode2();
                    if(!n1.equals(n2)) {
                        double newRank = rankValue(n1, n2);
                        if (newRank > 0) {
                            System.out.println("Adding to TABLE: Point: " + cnt.get() + ", Rank: " + newRank);
                            rankTable.put(new UndirectedEdge(n1, n2), (float) newRank);
                        }
                    }
                    cnt.getAndIncrement();
                });
                return null;
            };
        }

        @Override
        public Function<Graph, Double> computeCurrentScore() {
            return (graph)->0d;
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String,Collection<String>> test = new HashMap<>();
        test.put("n1",Arrays.asList("n2","n3"));
        test.put("n2",Arrays.asList("n1"));
        test.put("n3",Collections.emptyList());
        test.put("n4",Arrays.asList("n1","n2"));
        double damping = 0.75;
        SimRank pr = new SimRank(test,damping);
        pr.solve(10);
        //System.out.println("Similar to n4: "+String.join("; ",pr.findSimilarDocuments(Arrays.asList("n4"),3,4,2)));
    }

    public static class Loader {
        public Map<Edge,Float> loadRankTable(File file) {
            return new ObjectIO<Map<Edge,Float>>().load(file);
        }
    }
}
