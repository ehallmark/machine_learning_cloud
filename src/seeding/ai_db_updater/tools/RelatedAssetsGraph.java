package seeding.ai_db_updater.tools;

import model.graphs.Graph;
import model.graphs.MarkovNet;
import model.nodes.Node;
import seeding.Database;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/10/2017.
 */
public class RelatedAssetsGraph implements Serializable {
    private static final long serialVersionUID = 1;
    private static RelatedAssetsGraph SELF;
    private static final File graphFile = new File("data/related_assets_graph.jobj");
    private Map<Integer,Collection<String>> indexToAssetsMap;
    private Map<String,Integer> assetToIndexMap;
    private Graph graph;

    private RelatedAssetsGraph() {}

    public String relativesOf(String token) {
        return String.join("; ",relatives(token));
    }

    public Collection<String> relatives(String token) {
        Node node = graph.findNode(token);
        if(node==null)return Collections.emptyList();
        return node.getNeighbors().stream().map(n->n.getLabel()).collect(Collectors.toList());
    }

    public void init() {
        graph = new MarkovNet();
        AtomicInteger cnt = new AtomicInteger(0);
        Map<String,Collection<String>> combinedMap = new HashMap<>(Database.getPatentToRelatedPatentsMap());
        combinedMap.putAll(Database.getAppToRelatedPatentsMap());
        combinedMap.forEach((asset,related)->{
            Node n = graph.addNode(asset,1);
            related.forEach(rel->{
                graph.connectNodes(n, graph.addNode(rel,1));
            });
            if(cnt.getAndIncrement()%10000==0) System.out.println("Adding node: "+cnt.get());
        });
        // construct maps
        assetToIndexMap = new HashMap<>();
        indexToAssetsMap = new HashMap<>();
        AtomicInteger index = new AtomicInteger(0);
        Database.getAllPatentsAndApplications().forEach(asset->{
            if(!assetToIndexMap.containsKey(asset)) {
                Node node = graph.findNode(asset);
                Set<String> set = new HashSet<>();
                if (node != null) {
                    node.getNeighbors().forEach(n->{
                        assetToIndexMap.put(n.getLabel(), index.get());
                    });
                    set.addAll(node.getNeighbors().stream().map(n->n.getLabel()).collect(Collectors.toList()));
                }
                set.add(asset);
                assetToIndexMap.put(asset,index.get());
                indexToAssetsMap.put(index.get(),set);
                index.getAndIncrement();
                if(index.get()%10000==0) System.out.println("Adding index: "+index.get());
            }
        });
    }


    public synchronized static RelatedAssetsGraph get() {
        if(SELF==null) {
            SELF = (RelatedAssetsGraph) Database.tryLoadObject(graphFile);
        }
        return SELF;
    }

    public Collection<String> assetsFromIndex(int idx) {
        if(indexToAssetsMap.containsKey(idx)) {
            return new ArrayList<>(indexToAssetsMap.get(idx));
        }
        return Collections.emptyList();
    }

    public int indexForAsset(String asset) {
        if(assetToIndexMap.containsKey(asset)) {
            return assetToIndexMap.get(asset);
        }
        return -1;
    }


    public static void main(String[] args) {
        Database.initializeDatabase();
        SELF = new RelatedAssetsGraph();
        SELF.init();
        System.out.println("Done initializing... Saving model now.");
        Database.trySaveObject(SELF,graphFile);
    }
}
