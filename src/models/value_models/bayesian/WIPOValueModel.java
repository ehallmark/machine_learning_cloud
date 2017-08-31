package models.value_models.bayesian;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import elasticsearch.DataSearcher;
import lombok.NonNull;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.nodes.Node;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.*;
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
        AbstractAttribute wipo = new WIPOTechnologyAttribute();
        AbstractAttribute cpc = new CPCAttribute();
        AbstractAttribute assignee = new LatestAssigneeNestedAttribute().getAttributes().stream().filter(attr->attr.getName().equals(Constants.ASSIGNEE)).findFirst().orElse(null);
        AbstractAttribute docKind = new DocKindAttribute();
        Collection<AbstractAttribute> attributes = Arrays.asList(
                wipo,
                cpc,
                docKind,
                assignee
        );
        Set<String> attrNameSet = attributes.stream().map(attr->attr.getFullName()).collect(Collectors.toSet());
        Map<String,Boolean> gatherValueMap = Database.getGatherValueMap();
        AbstractIncludeFilter gatherFilter = new AbstractIncludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, new ArrayList<>(gatherValueMap.keySet()));
        Collection<AbstractFilter> filters = Arrays.asList(gatherFilter);

        Map<String,NestedAttribute> nestedMap = new HashMap<>();
        nestedMap.put(Constants.LATEST_ASSIGNEE, new LatestAssigneeNestedAttribute());
        List<Item> items = new ArrayList<>(Arrays.asList(DataSearcher.searchForAssets(attributes, filters, Constants.NAME, SortOrder.ASC, maxLimit, nestedMap)));
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

            attributes.forEach(attr->{
                Object obj = item.getData(attr.getFullName());
                if(obj!=null) {
                    nestedHelper(attr.getFullName(), obj, attrNameSet, valueVariableName, variableToValuesMap);
                }
                if(attr.equals(assignee)) {
                    System.out.println("Attr for assignee.getName: "+item.getData(assignee.getName()));
                    System.out.println("Attr for assignee.getFullName: "+item.getData(assignee.getFullName()));
                    System.out.println("Attr for assignee.rootName: "+item.getData(assignee.getRootName()));
                }
            });
            item.getDataMap().forEach((attr,obj)->{
                if(obj instanceof Map) {
                    System.out.println("Checking map: "+attr);
                    ((Map<String,Object>)obj).forEach((innerAttr,innerObj)->{
                        nestedHelper(attr+"."+innerAttr, innerObj, attrNameSet, valueVariableName, variableToValuesMap);
                    });
                } else {
                    nestedHelper(attr, obj, attrNameSet, valueVariableName, variableToValuesMap);
                }
            });
        });
        // sanity check
        variableToValuesMap.forEach((var,values)->{
            //System.out.println("Values for "+var+": "+String.join("; ",values));
        });
        attributes.forEach(attr->{
            if(!variableToValuesMap.keySet().contains(attr.getFullName())) {
                System.out.println("Keys found: "+String.join("; ",variableToValuesMap.keySet()));
                throw new RuntimeException("Missing attribute: "+attr.getFullName());
            }
        });
        variableToValuesMap.forEach((attr,values)->{
            if(values.size()<2) throw new RuntimeException("Each variable must have at least 2 states: "+attr);
            graph.addNode(attr,values.size());
        });

        Node valueNode = graph.findNode(valueVariableName);
        Node wipoNode = graph.findNode(wipo.getFullName());
        Node cpcNode = graph.findNode(cpc.getFullName());
        Node docKindNode = graph.findNode(docKind.getFullName());
        Node assigneeNode = graph.findNode(assignee.getFullName());

        // connect and add factors
        graph.connectNodes(valueNode, wipoNode);
        graph.connectNodes(valueNode, cpcNode);
        graph.connectNodes(valueNode, assigneeNode);
        graph.connectNodes(wipoNode, cpcNode);
        graph.connectNodes(valueNode, docKindNode);
        graph.addFactorNode(null, valueNode);
        graph.addFactorNode(null, valueNode, wipoNode, cpcNode);
        graph.addFactorNode(null, valueNode, assigneeNode);
        graph.addFactorNode(null, valueNode, docKindNode);

        bayesianValueModel = new BayesianValueModel(graph,alpha,trainingItems,variableToValuesMap,valueVariableName);
        bayesianValueModel.train();
    }

    private static void nestedHelper(String attr, Object obj, Collection<String> attrNameSet, String valueVariableName, Map<String,List<String>> variableToValuesMap) {
        if(obj==null||(!attrNameSet.contains(attr)&&!attr.equals(valueVariableName))) return;
        System.out.println("Checking attr: "+attr);
        Collection<String> objects = obj.toString().contains("; ") ? Arrays.asList(obj.toString().split("; ")) : Arrays.asList(obj.toString());
        if(variableToValuesMap.containsKey(attr)) {
            objects.forEach(val->{
                if(!variableToValuesMap.get(attr).contains(val)) {
                    variableToValuesMap.get(attr).add(val);
                }
            });
        } else {
            List<String> valuesSet = Collections.synchronizedList(new ArrayList<>());
            valuesSet.addAll(objects);
            variableToValuesMap.put(attr,valuesSet);
        }
    }

    public static void main(String[] args) {
        WIPOValueModel model = new WIPOValueModel();
        model.init();
        File csv = new File(Constants.DATA_FOLDER+"value-graph-wipo.csv");
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(csv))) {
            writer.write("asset,wipoValue,gatherValue\n");
            for (Item item : model.testItems) {
                double value = model.bayesianValueModel.evaluate(item);
               // System.out.println(item.getName() + "," + value);
                writer.write(item.getName() + "," + value + "," + Database.getGatherValueMap().get(item.getName())+"\n");
            }
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
