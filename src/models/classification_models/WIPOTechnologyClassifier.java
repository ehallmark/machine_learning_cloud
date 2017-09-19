package models.classification_models;

import org.deeplearning4j.berkeley.Pair;

import java.util.*;

/**
 * Created by Evan on 3/4/2017.
 */
public class WIPOTechnologyClassifier extends ClassificationAttr {
    public String getName() {
        return "WIPO Technology Tagger Model";
    }

    @Override
    public void save() {
        // do nothing
    }

    @Override
    public void train(Map<String, Collection<String>> trainingData) {

    }

    @Override
    public ClassificationAttr optimizeHyperParameters(Map<String, Collection<String>> trainingData, Map<String, Collection<String>> validationData) {
        return this;
    }

    public int numClassifications() {
        return WIPOHelper.getDefinitionMap().size();
    }

    public Collection<String> getClassifications() { return new ArrayList<>(WIPOHelper.getOrderedClassifications()); }

    @Override
    public ClassificationAttr untrainedDuplicate() {
        return new WIPOTechnologyClassifier();
    }


    private static List<Pair<String,Double>> wipoHelper(Collection<String> patents, int limit) {
        // TODO hookup to elasticsearch
        return Collections.emptyList();
    }

    @Override
    public List<Pair<String, Double>> attributesFor(Collection<String> portfolio, int n) {
        return wipoHelper(portfolio,n);
    }

}
