package models.value_models.bayesian.page_rank;

import lombok.Getter;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.learning.algorithms.LearningAlgorithm;
import model.nodes.Node;

import java.util.*;

/**
 * Created by ehallmark on 4/21/17.
 */
public abstract class RankGraph<K> {
    protected Graph graph;
    protected List<Node> nodes;
    protected double damping;
    @Getter
    protected Map<K,Float> rankTable;

    protected RankGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> importantLabels, double damping, Graph graph) {
        System.out.println("Initializing RankGraph of type: "+this.getClass().getName());
        if(damping<0||damping>1) throw new RuntimeException("Illegal damping constant");
        this.graph=graph;
        this.damping=damping;
        this.initGraph(labelToCitationLabelsMap,importantLabels);
        System.out.println("Finished "+this.getClass().getName());
    }
    // default
    protected RankGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> importantLabels, double damping) {
        this(labelToCitationLabelsMap,importantLabels,damping,new BayesianNet());
    }

    protected abstract void initGraph(Map<String, ? extends Collection<String>> labelToCitationLabelsMap, Collection<String> importantLabels);

    protected abstract LearningAlgorithm getLearningAlgorithm();

    public void solve(int numEpochs) {
        graph.applyLearningAlgorithm(getLearningAlgorithm(),numEpochs);
    }

}
