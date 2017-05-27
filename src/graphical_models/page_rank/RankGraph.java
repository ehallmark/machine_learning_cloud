package graphical_models.page_rank;

import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.graphs.MarkovNet;
import model.learning.algorithms.LearningAlgorithm;
import model.nodes.Node;
import util.ObjectIO;

import java.io.*;
import java.util.*;

/**
 * Created by ehallmark on 4/21/17.
 */
public abstract class RankGraph<K> {
    protected Graph graph;
    protected List<Node> nodes;
    protected double damping;
    protected Map<K,Float> rankTable;

    protected RankGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, double damping, Graph graph) {
        System.out.println("Initializing RankGraph of type: "+this.getClass().getName());
        if(damping<0||damping>1) throw new RuntimeException("Illegal damping constant");
        this.graph=graph;
        this.damping=damping;
        this.initGraph(labelToCitationLabelsMap);
        System.out.println("Finished "+this.getClass().getName());
    }
    // default
    protected RankGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, double damping) {
        this(labelToCitationLabelsMap,damping,new MarkovNet());
    }

    protected abstract void initGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap);

    protected abstract LearningAlgorithm getLearningAlgorithm();

    public void solve(int numEpochs) {
        graph.applyLearningAlgorithm(getLearningAlgorithm(),numEpochs);
    }

    public void save(File file) {
        new ObjectIO().save(file, rankTable);
    }

}
