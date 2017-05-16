package svm;

import org.deeplearning4j.berkeley.Pair;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.portfolios.AbstractPortfolio;

import java.util.Collection;
import java.util.List;

/**
 * Created by Evan on 5/16/2017.
 */
public class GatherSVMClassifier extends ClassificationAttr {
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

    public static void main(String[] args) {
        // build gather svm

    }
}
