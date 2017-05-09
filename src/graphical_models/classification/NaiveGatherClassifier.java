package graphical_models.classification;

import model.graphs.BayesianNet;
import org.deeplearning4j.berkeley.Pair;
import ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 5/1/17.
 */
public class NaiveGatherClassifier {
    protected BayesianNet bayesianNet;

    @Override
    public double getTechnologyValueFor(Collection<String> items, String technology, PortfolioList.Type type) {
        return 0;
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        return null;
    }

    @Override
    public Collection<String> getAllTechnologies() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }
}
