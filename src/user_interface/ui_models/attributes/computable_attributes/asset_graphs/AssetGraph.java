package user_interface.ui_models.attributes.computable_attributes.asset_graphs;

import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.graphs.MarkovNet;
import model.nodes.Node;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

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
    protected ComputableAttribute<? extends Collection<String>>[] dependentAttributes;
    private int depth;
    protected AssetGraph(boolean directed, int depth, ComputableAttribute<? extends Collection<String>>... dependentAttributes) {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
        this.directed=directed;
        this.depth=depth;
        this.dependentAttributes=dependentAttributes;
    }

    @Override
    public List<String> handleIncomingData(String item, Map<String, Object> data, Map<String,List<String>> myData, boolean isApplication) {
        // prevents default behavior
        return null;
    }

    private List<String> relatives(String token) {
        Node node = graph.findNode(token);
        return relativeHelper(node,0).stream().map(d->d.getLabel()).distinct().collect(Collectors.toList());
    }

    private List<Node> relativeHelper(Node node, int d) {
        if(node==null || d >= depth)return Collections.emptyList();
        return (directed ? node.getInBound() : node.getNeighbors()).stream().flatMap(n->Stream.of(Stream.of(n),relativeHelper(n,d+1).stream()).flatMap(s->s)).collect(Collectors.toList());
    }

    public void initAndSave(boolean testing) {
        graph = (directed) ? new BayesianNet() : new MarkovNet();

        int numPatents = Stream.of(dependentAttributes).mapToInt(d->d.getPatentDataMap().size()).max().getAsInt();
        int numApplications = Stream.of(dependentAttributes).mapToInt(d->d.getApplicationDataMap().size()).max().getAsInt();

        System.out.println("Test : "+getFullName());
        System.out.println("  Num patents: "+numPatents);
        System.out.println("  Num applications: "+numApplications);

        Set<String> patents = new HashSet<>();
        Set<String> applications = new HashSet<>();

        for(int i = 0; i < dependentAttributes.length; i++) {
            Map<String,? extends Collection<String>> patentMap = dependentAttributes[i].getPatentDataMap();
            Map<String,? extends Collection<String>> applicationMap = dependentAttributes[i].getApplicationDataMap();

            patents.addAll(patentMap.keySet());
            applications.addAll(applicationMap.keySet());

            Map<String,Collection<String>> combinedMap = Stream.of(patentMap.entrySet(),applicationMap.entrySet())
                    .parallel()
                    .flatMap(set->set.stream())
                    .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));
            constructGraph(combinedMap);
        }

        // build maps
        patentDataMap = Collections.synchronizedMap(new HashMap<>());
        applicationDataMap = Collections.synchronizedMap(new HashMap<>());

        addToMap(patents, patentDataMap);
        addToMap(applications, applicationDataMap);

        System.out.println("  Num patents before: " + numPatents);
        System.out.println("  Num applications before: " + numApplications);
        System.out.println("  Num patents after: " + patentDataMap.size());
        System.out.println("  Num applications after: " + applicationDataMap.size());


        if(!testing) {
            super.save();
        }
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
