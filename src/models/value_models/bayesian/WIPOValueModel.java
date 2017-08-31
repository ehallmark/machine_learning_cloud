package models.value_models.bayesian;

import elasticsearch.DataSearcher;
import lombok.NonNull;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.computable_attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.filters.AbstractExcludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;

/**
 * Created by Evan on 8/30/2017.
 */
public class WIPOValueModel {
    private BayesianValueModel bayesianValueModel;

    public void init() {
        final String valueVariableName = "gatherValue";
        final double alpha = 20d;
        final int maxLimit = 10000;
        final Graph graph = new BayesianNet();

        Collection<AbstractAttribute> attributes = Arrays.asList(
                new WIPOTechnologyAttribute(),
                new AssetNumberAttribute()
        );
        Map<String,Boolean> gatherValueMap = Database.getGatherValueMap();
        AbstractIncludeFilter gatherFilter = new AbstractIncludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, new ArrayList<>(gatherValueMap.keySet()));
        Collection<AbstractFilter> filters = Arrays.asList(gatherFilter);

        Item[] trainingItems = DataSearcher.searchForAssets(attributes, filters, "name", SortOrder.ASC, maxLimit, new HashMap<>());
        final Map<String,List<String>> variableToValuesMap = Collections.synchronizedMap(new HashMap<>());
        Arrays.stream(trainingItems).parallel().forEach(item->{
            // add gather value
            Boolean value = gatherValueMap.get(item.getName());
            if(value==null) return;
            item.addData(valueVariableName, value ? 1 : 0);
            // add other values
            item.getDataMap().forEach((attr,obj)->{
                if(obj==null) return;
                if(variableToValuesMap.containsKey(attr) && !variableToValuesMap.get(attr).contains(obj.toString())) {
                    variableToValuesMap.get(attr).add(obj.toString());
                } else {
                    List<String> valuesSet = Collections.synchronizedList(new ArrayList<>());
                    valuesSet.add(obj.toString());
                    variableToValuesMap.put(attr,valuesSet);
                }
            });
        });
        // sanity check
        variableToValuesMap.forEach((var,values)->{
            System.out.println("Values for "+var+": "+String.join("; ",values));
        });
        attributes.forEach(attr->{
            if(!variableToValuesMap.keySet().contains(attr.getFullName())) {
                throw new RuntimeException("Missing attribute: "+attr.getFullName());
            }
        });
        variableToValuesMap.forEach((attr,values)->{
            graph.addNode(attr,values.size());
        });

        // connect and add factors



        bayesianValueModel = new BayesianValueModel(graph,alpha,trainingItems,variableToValuesMap,valueVariableName);
    }
}
