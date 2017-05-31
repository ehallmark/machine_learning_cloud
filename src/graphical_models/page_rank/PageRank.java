package graphical_models.page_rank;

import com.google.common.util.concurrent.AtomicDouble;
import model.graphs.Graph;
import model.learning.algorithms.LearningAlgorithm;
import model.nodes.Node;
import util.ObjectIO;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/21/17.
 */
public class PageRank extends RankGraph<String> {
    protected double currentScore;

    public PageRank(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, double damping) {
        super(labelToCitationLabelsMap, null,damping);
    }

    @Override
    public LearningAlgorithm getLearningAlgorithm() {
        return new Algorithm();
    }

    protected double rankValue(Node node) {
        return (1d-damping)/nodes.size() + damping * node.getNeighbors().stream().collect(Collectors.summingDouble(neighbor->{
            Float rank = rankTable.get(neighbor.getLabel());
            if(rank==null) return 0d;
            if(neighbor.getNeighbors().size()>0) {
                return (double)rank/neighbor.getNeighbors().size();
            } else return 0d;
        }));
    }

    @Override
    protected void initGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> labels) {
        rankTable=new HashMap<>(labelToCitationLabelsMap.size());
        System.out.println("Adding initial nodes...");
        labelToCitationLabelsMap.forEach((label,citations)->{
            Node labelNode = graph.addBinaryNode(label);
            citations.forEach(citation->{
                Node citationNode = graph.addBinaryNode(citation);
                graph.connectNodes(labelNode,citationNode);
            });
        });
        nodes=graph.getAllNodesList();
        this.nodes.forEach(node->rankTable.put(node.getLabel(),1f/nodes.size()));
        System.out.println("Done.");
    }


    public class Algorithm implements LearningAlgorithm {
        @Override
        public boolean runAlgorithm() {
            AtomicInteger cnt = new AtomicInteger(0);
            AtomicDouble delta = new AtomicDouble(0d);
            nodes.stream().forEach(node -> {
                double rank = rankValue(node);
                double prior = rankTable.get(node.getLabel());
                rankTable.put(node.getLabel(), (float) rank);
                delta.getAndAdd(Math.abs(rank-prior));
                if(cnt.getAndIncrement()%10000==0) System.out.println("Updated scores of "+cnt.get()+" patents so far. Score="+(delta.get()/cnt.get()));
            });
            currentScore = delta.get()/nodes.size();
            return currentScore  < 0.0000001/nodes.size();
        }

        @Override
        public double computeCurrentScore() {
            return currentScore;
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
