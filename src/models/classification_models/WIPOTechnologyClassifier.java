package models.classification_models;

import elasticsearch.DataSearcher;
import org.deeplearning4j.berkeley.Pair;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        if(patents.isEmpty()) return Collections.emptyList();
        Item[] items = DataSearcher.searchForAssets(Arrays.asList(new WIPOTechnologyAttribute()), Arrays.asList(new AbstractIncludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, patents)), null, SortOrder.ASC, 10000, new HashMap<>(),false);
        return Stream.of(items).map(item->item.getData(Constants.WIPO_TECHNOLOGY)).filter(tech->tech!=null).collect(Collectors.groupingBy(tech->tech.toString(),Collectors.counting()))
                .entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).limit(limit)
                .map(e->new Pair<>(e.getKey(),e.getValue().doubleValue()/patents.size())).collect(Collectors.toList());
    }

    @Override
    public List<Pair<String, Double>> attributesFor(Collection<String> portfolio, int n) {
        return wipoHelper(portfolio,n);
    }

}
