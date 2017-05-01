package citation_graph.page_rank;

import model.graphs.Graph;
import model.learning.algorithms.LearningAlgorithm;
import model.nodes.Node;
import util.ObjectIO;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/21/17.
 */
public class PageRank extends RankGraph<String> {
    public PageRank(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, double damping) {
        super(labelToCitationLabelsMap, damping);
    }

    @Override
    public LearningAlgorithm getLearningAlgorithm() {
        return new Algorithm();
    }

    protected double rankValue(Node node) {
        return (1d-damping)/nodes.size() + damping * node.getInBound().stream().collect(Collectors.summingDouble(neighbor->{
            Float rank = rankTable.get(neighbor.getLabel());
            if(rank==null)rank=0f;
            if(neighbor.getInBound().size()>0) {
                return (double)rank/neighbor.getInBound().size();
            } else return 0d;
        }));
    }

    @Override
    protected void initGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap) {
        rankTable=new HashMap<>(labelToCitationLabelsMap.size()*labelToCitationLabelsMap.size());
        System.out.println("Adding initial nodes...");
        labelToCitationLabelsMap.forEach((label,citations)->{
            graph.addBinaryNode(label);
            citations.forEach(citation->{
                graph.addBinaryNode(citation);
                graph.connectNodes(label, citation);
            });
        });
        nodes=graph.getAllNodesList();
        nodes.forEach(node->{
            rankTable.put(node.getLabel(),1f/nodes.size());
        });
        System.out.println("Done.");
    }


    public class Algorithm implements LearningAlgorithm {
        @Override
        public Function<Graph, Void> runAlgorithm() {
            return (graph) -> {
                nodes.stream().forEach(node -> {
                    double rank = rankValue(node);
                    if (rank > 0) {
                        System.out.println("Score for "+node.getLabel()+": "+rank);
                        rankTable.put(node.getLabel(), (float) rank);
                    }
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
        test.put("n1", Arrays.asList("n2","n3"));
        test.put("n2",Arrays.asList("n1"));
        test.put("n3", Collections.emptyList());
        test.put("n4",Arrays.asList("n1","n2"));
        double damping = 0.75;
        PageRank pr = new PageRank(test,damping);
        pr.solve(10);
        //System.out.println("Similar to n4: "+String.join("; ",pr.findSimilarDocuments(Arrays.asList("n4"),3,4,2)));
    }

    public static class Loader {
        public Map<String,Float> loadRankTable(File file) {
            return new ObjectIO<Map<String,Float>>().load(file);
        }
    }
}
