package models.value_models.bayesian;

import model.graphs.BayesianNet;
import model.graphs.Graph;

/**
 * Created by ehallmark on 8/30/17.
 */
public class BayesianValueModel {
    protected Graph graph;
    public BayesianValueModel() {

    }

    public void initModel() {
        graph = new BayesianNet();
       // graph.addNode
    }
}
