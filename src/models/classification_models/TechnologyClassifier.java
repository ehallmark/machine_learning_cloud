package models.classification_models;

import org.nd4j.linalg.primitives.PairBackup;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 3/4/2017.
 */
public class TechnologyClassifier extends ClassificationAttr {
    private ComputableAttribute<? extends Collection<String>> attribute;
    public TechnologyClassifier(ComputableAttribute<? extends Collection<String>> attribute) {
        this.attribute=attribute;
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

    public Collection<String> getClassifications() { return attribute.getAllValues(); }

    @Override
    public ClassificationAttr untrainedDuplicate() {
        return new TechnologyClassifier(attribute);
    }


    private List<PairBackup<String,Double>> technologyHelper(Collection<String> patents, int limit, Boolean isApp) {
        if(patents.isEmpty()) return Collections.emptyList();
        return patents.stream().flatMap(item->{
            Collection<String> attributes = attribute.attributesFor(Arrays.asList(item),1,isApp);
            if(attributes==null) return Stream.empty();
            else return attributes.stream();
        }).filter(tech->tech!=null).collect(Collectors.groupingBy(tech->tech,Collectors.counting()))
                .entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(limit)
                .map(e->new PairBackup<>(e.getKey(),e.getValue().doubleValue()/patents.size())).collect(Collectors.toList());
    }

    @Override
    public List<PairBackup<String, Double>> attributesFor(Collection<String> portfolio, int n) {
        return attributesFor(portfolio,n,null);
    }

    public List<PairBackup<String, Double>> attributesFor(Collection<String> portfolio, int n, Boolean isApp) {
        return technologyHelper(portfolio,n, isApp);
    }

}
