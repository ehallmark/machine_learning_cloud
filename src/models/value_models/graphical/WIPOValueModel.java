package models.value_models.graphical;

import elasticsearch.DataSearcher;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.nodes.FactorNode;
import model.nodes.Node;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.computable_attributes.ExistsInGatherFilter;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * Created by Evan on 8/30/2017.
 */
public class WIPOValueModel extends ValueAttr {
    private static final boolean debug = false;

    private GraphicalValueModel bayesianValueModel;
    private Item[] allItems;
    private static Map<String,Double> technologyToFactorMap;
    private static final File technologyToFactorFile = new File(Constants.DATA_FOLDER+"wipo_technology_to_gather_factor_map.jobj");

    @Override
    public double evaluate(Item item) {
        if(technologyToFactorMap==null) {
            synchronized (WIPOValueModel.class) {
                if(technologyToFactorMap==null) {
                    technologyToFactorMap = (Map<String, Double>) Database.loadObject(technologyToFactorFile);
                }
            }
        }
        Object wipoTech = item.getData(Constants.WIPO_TECHNOLOGY);
        double val;
        if(wipoTech==null) val = 0d;
        else val = technologyToFactorMap.getOrDefault(wipoTech,0d);
        if(debug) System.out.println("WIPO TECHNOLOGY VALUE: "+val);
        return val;
    }


    @Override
    public String getName() {
        return "wipoTechnologyValue";
    }

    public void init() {
        final double alpha = 10d;
       // final String valueVariableName = "gatherValue";
        final int maxLimit = 100000;
        AbstractAttribute wipo = new WIPOTechnologyAttribute();
        //AbstractAttribute filingCountry = new LatestAssigneeNestedAttribute().getAttributes().stream().filter(attr->attr.getName().equals(Constants.COUNTRY)).findFirst().get();
        Collection<AbstractAttribute> attributes = Arrays.asList(
                wipo
        );
        Set<String> attrNameSet = attributes.stream().map(attr->attr.getFullName()).collect(Collectors.toSet());
        AbstractIncludeFilter gatherFilter = new ExistsInGatherFilter();
        Collection<AbstractFilter> filters = Arrays.asList(gatherFilter, new AbstractIncludeFilter(new ResultTypeAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, Arrays.asList("patents")));
        Map<String,NestedAttribute> nestedMap = new HashMap<>();
        nestedMap.put(Constants.LATEST_ASSIGNEE, new LatestAssigneeNestedAttribute());
        List<Item> items = new ArrayList<>(Arrays.asList(DataSearcher.searchForAssets(attributes, filters, Constants.NAME, SortOrder.ASC, maxLimit, nestedMap,false)).stream().filter(item->Database.getGatherValueMap().containsKey(item.getName())).collect(Collectors.toList()));
        System.out.println("Num items: "+items.size());
        allItems = items.toArray(new Item[items.size()]);

        Map<String,Boolean> gatherValueMap = Database.getGatherValueMap();
        final Map<String,List<String>> variableToValuesMap = Collections.synchronizedMap(new HashMap<>());
        Arrays.stream(allItems).parallel().forEach(item->{
            // add gather value
          //  Boolean value = gatherValueMap.get(item.getName());
          //  if(value==null) value = false;
          //  item.addData(valueVariableName, value ? 1 : 0);

            // add values
            attributes.forEach(attr->{
                Object obj = item.getData(attr.getFullName());
                if(obj!=null) {
                    nestedHelper(attr.getFullName(), obj, attrNameSet, variableToValuesMap, null);
                }
            });
            item.getDataMap().forEach((attr,obj)->{
                if(obj instanceof Map) {
                    ((Map<String,Object>)obj).forEach((innerAttr,innerObj)->{
                        nestedHelper(attr+"."+innerAttr, innerObj, attrNameSet, variableToValuesMap, null);
                    });
                } else {
                    nestedHelper(attr, obj, attrNameSet, variableToValuesMap, null);
                }
            });
        });
        // sanity check
        //variableToValuesMap.forEach((var,values)->{
            //System.out.println("Values for "+var+": "+String.join("; ",values));
        //});
        attributes.forEach(attr->{
            if(!variableToValuesMap.keySet().contains(attr.getFullName())) {
                System.out.println("Keys found: "+String.join("; ",variableToValuesMap.keySet()));
                throw new RuntimeException("Missing attribute: "+attr.getFullName());
            }
        });

        // Create graphical model
        final Graph graph = new BayesianNet();
        variableToValuesMap.forEach((attr,values)->{
            if(values.size()<2) throw new RuntimeException("Each variable must have at least 2 states: "+attr + " ["+String.join("; ",values)+"]");
            graph.addNode(attr,values.size());
        });

        //Node valueNode = graph.findNode(valueVariableName);
        Node wipoNode = graph.findNode(wipo.getFullName());
        // connect and add factors
       // graph.connectNodes(wipoNode, valueNode);
        graph.addFactorNode(null, wipoNode);
        //graph.addFactorNode(null, valueNode, wipoNode);

        bayesianValueModel = new GraphicalValueModel(getName(),graph,alpha,allItems,variableToValuesMap, null);
        bayesianValueModel.train();

        bayesianValueModel.graph.setCurrentAssignment(new HashMap<>());
        FactorNode wipoFactor = bayesianValueModel.graph.variableElimination(new String[]{Constants.WIPO_TECHNOLOGY});
        wipoFactor.reNormalize(array->{
            double max = DoubleStream.of(array).max().getAsDouble();
            for(int i = 0; i < array.length; i++ ) {
                array[i] /= max;
            }
        });

        List<String> technologies = bayesianValueModel.getVariableToValuesMap().get(Constants.WIPO_TECHNOLOGY);
        technologyToFactorMap = Collections.synchronizedMap(new HashMap<>());
        if(technologies.size()!=wipoFactor.getWeights().length) throw new RuntimeException("Illegal number of weights: "+technologies.size()+" != "+wipoFactor.getWeights().length);
        for(int i = 0; i < wipoFactor.getWeights().length; i++) {
            System.out.println("Weight for "+technologies.get(i)+": "+wipoFactor.getWeights()[i]);
            technologyToFactorMap.put(technologies.get(i),wipoFactor.getWeights()[i]);
        }
    }

    private static void nestedHelper(String attr, Object obj, Collection<String> attrNameSet, Map<String,List<String>> variableToValuesMap, String valueVariableName) {
        if(obj==null||(!attrNameSet.contains(attr)&&(valueVariableName==null||!attr.equals(valueVariableName)))) return;
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

    @Override
    public void save() {
        if (technologyToFactorMap != null && technologyToFactorMap.size() > 0) {
            Database.trySaveObject(technologyToFactorMap,technologyToFactorFile);
        }
    }

    public static void main(String[] args) {
        WIPOValueModel model = new WIPOValueModel();
        model.init();
        model.save();
    }

}
