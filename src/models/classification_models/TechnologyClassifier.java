package models.classification_models;

import elasticsearch.DataSearcher;
import org.deeplearning4j.berkeley.Pair;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

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


    private List<Pair<String,Double>> technologyHelper(Collection<String> patents, int limit) {
        if(patents.isEmpty()) return Collections.emptyList();
        return patents.stream().flatMap(item->attribute.attributesFor(Arrays.asList(item),1).stream()).filter(tech->tech!=null).collect(Collectors.groupingBy(tech->tech,Collectors.counting()))
                .entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(limit)
                .map(e->new Pair<>(e.getKey(),e.getValue().doubleValue()/patents.size())).collect(Collectors.toList());
    }

    @Override
    public List<Pair<String, Double>> attributesFor(Collection<String> portfolio, int n) {
        return technologyHelper(portfolio,n);
    }

}
