package models.value_models.bayesian;

import elasticsearch.DataSearcher;
import lombok.NonNull;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.nodes.Node;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.computable_attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.filters.AbstractExcludeFilter;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 8/30/2017.
 */
public class WIPOValueModel {
    private BayesianValueModel bayesianValueModel;
    private Item[] testItems;

    public void init() {
        final String valueVariableName = "gatherValue";
        final double alpha = 20d;
        final int maxLimit = 10000;
        final Graph graph = new BayesianNet();
        AbstractAttribute factor = new WIPOTechnologyAttribute();

        Collection<AbstractAttribute> attributes = Arrays.asList(
                factor
        );
        Set<String> attrNameSet = attributes.stream().map(attr->attr.getFullName()).collect(Collectors.toSet());
        Map<String,Boolean> gatherValueMap = Database.getGatherValueMap();
        AbstractIncludeFilter gatherFilter = new AbstractIncludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, new ArrayList<>(gatherValueMap.keySet()));
        Collection<AbstractFilter> filters = Arrays.asList(gatherFilter);

        List<Item> items = new ArrayList<>(Arrays.asList(DataSearcher.searchForAssets(attributes, filters, Constants.NAME, SortOrder.ASC, maxLimit, new HashMap<>())));
        System.out.println("Num items: "+items.size());
        Collections.shuffle(items, new Random(69));
        testItems = items.subList(0, items.size()/2).toArray(new Item[]{});
        Item[] trainingItems = items.subList(items.size()/2, items.size()).toArray(new Item[]{});

        final Map<String,List<String>> variableToValuesMap = Collections.synchronizedMap(new HashMap<>());
        Arrays.stream(trainingItems).parallel().forEach(item->{
            // add gather value
            Boolean value = gatherValueMap.get(item.getName());
            if(value==null) return;
            item.addData(valueVariableName, value ? 1 : 0);
            // add other values
            item.getDataMap().forEach((attr,obj)->{
                if(obj==null||(!attrNameSet.contains(attr)&&!attr.equals(valueVariableName))) return;
                if(variableToValuesMap.containsKey(attr)) {
                    if(!variableToValuesMap.get(attr).contains(obj.toString())) {
                        variableToValuesMap.get(attr).add(obj.toString());
                    }
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
            if(values.size()<2) throw new RuntimeException("Each variable must have at least 2 states: "+attr);
            graph.addNode(attr,values.size());
        });

        Node valueNode = graph.findNode(valueVariableName);
        Node wipoNode = graph.findNode(factor.getFullName());

        // connect and add factors
        graph.connectNodes(valueNode, wipoNode);
        graph.addFactorNode(null, valueNode);
        graph.addFactorNode(null, valueNode, wipoNode);

        bayesianValueModel = new BayesianValueModel(graph,alpha,trainingItems,variableToValuesMap,valueVariableName);
        bayesianValueModel.train();
    }

    public static void main(String[] args) {
        WIPOValueModel model = new WIPOValueModel();
        model.init();
        File csv = new File(Constants.DATA_FOLDER+"value-graph-wipo.csv");
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(csv))) {
            writer.write("asset,wipoValue,gatherValue\n");
            for (Item item : model.testItems) {
                double value = model.bayesianValueModel.evaluate(item);
                System.out.println(item.getName() + "," + value);
                writer.write(item.getName() + "," + value + "," + Database.getGatherValueMap().get(item.getName())+"\n");
            }
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
