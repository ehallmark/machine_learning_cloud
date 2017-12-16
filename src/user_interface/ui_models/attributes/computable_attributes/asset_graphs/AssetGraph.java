package user_interface.ui_models.attributes.computable_attributes.asset_graphs;

import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.graphs.MarkovNet;
import model.nodes.Node;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 7/10/2017.
 */
public abstract class AssetGraph extends ComputableAttribute<List<String>> {
    private Graph graph;
    private boolean directed;
    private ComputableAttribute<? extends Collection<String>> dependentAttribute;
    protected AssetGraph(boolean directed, ComputableAttribute<? extends Collection<String>> dependentAttribute) {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
        this.directed=directed;
        this.dependentAttribute=dependentAttribute;
    }

    @Override
    public List<String> handleIncomingData(String item, Map<String, Object> data, Map<String,List<String>> myData, boolean isApplication) {
        // prevents default behavior
        return null;
    }

    private List<String> relatives(String token) {
        Node node = graph.findNode(token);
        if(node==null)return Collections.emptyList();
        return (directed ? node.getInBound() : node.getNeighbors()).stream().map(n->n.getLabel()).collect(Collectors.toList());
    }

    public void initAndSave() {
        graph = (directed) ? new BayesianNet() : new MarkovNet();
        Map<String,Collection<String>> combinedMap = Stream.of(dependentAttribute.getPatentDataMap().entrySet(),dependentAttribute.getApplicationDataMap().entrySet())
                .parallel()
                .flatMap(set->set.stream())
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
        constructGraph(combinedMap);
        // build maps
        patentDataMap = Collections.synchronizedMap(new HashMap<>());
        applicationDataMap = Collections.synchronizedMap(new HashMap<>());

        addToMap(dependentAttribute.getPatentDataMap().keySet(),patentDataMap);
        addToMap(dependentAttribute.getApplicationDataMap().keySet(),applicationDataMap);
        super.save();
    }

    @Override
    public void save() {
    }

    private void addToMap(Collection<String> keys, Map<String,List<String>> map) {
        keys.parallelStream().forEach(key->{
            List<String> related = relatives(key);
            if(related!=null && related.size() > 0) {
                map.put(key, related);
            }
        });
    }

    private void constructGraph(Map<String,Collection<String>> combineMap) {
        AtomicInteger cnt = new AtomicInteger(0);
        combineMap.forEach((asset,related)->{
            Node n = graph.addNode(asset,1);
            related.forEach(rel->{
                Node relNode = graph.addNode(rel, 1);
                graph.connectNodes(n, relNode);
            });
            if(cnt.getAndIncrement()%10000==0) System.out.println("Adding node: "+cnt.get());
        });
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }


}
