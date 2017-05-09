package graphical_models.classification;

import model.graphs.BayesianNet;
import org.deeplearning4j.berkeley.Pair;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.portfolios.AbstractPortfolio;

import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 5/1/17.
 */
public class NaiveGatherClassifier extends ClassificationAttr{
    protected BayesianNet bayesianNet;

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int limit) {
        return null;
    }

    @Override
    public int numClassifications() {
        return 0;
    }

    @Override
    public Collection<String> getClassifications() {
        return null;
    }
}
