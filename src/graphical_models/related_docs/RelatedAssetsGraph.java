package graphical_models.related_docs;

import model.graphs.Graph;
import model.graphs.MarkovNet;
import model.nodes.Node;
import seeding.Database;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/10/2017.
 */
public class RelatedAssetsGraph implements Serializable {
    private static RelatedAssetsGraph SELF;
    private static final File FILE = new File("data/related_assets_graph.jobj");
    private Graph graph;

    private RelatedAssetsGraph() {}

    public String relativesOf(String token) {
        Node node = graph.findNode(token);
        if(token==null) return "";
        return String.join("; ",node.getNeighbors().stream().map(n->n.getLabel()).collect(Collectors.toList()));
    }

    public void init() {
        graph = new MarkovNet();
        AtomicInteger cnt = new AtomicInteger(0);
        Database.getPatentToRelatedPatentsMap().forEach((asset,related)->{
            Node n = graph.addNode(asset,1);
            related.forEach(rel->{
                graph.connectNodes(n, graph.addNode(rel,1));
            });
            if(cnt.getAndIncrement()%10000==0) System.out.println("Adding node: "+cnt.get());
        });
    }


    public static RelatedAssetsGraph get() {
        if(SELF==null) {
            if(FILE.exists()) {
                SELF = (RelatedAssetsGraph) Database.tryLoadObject(FILE);
            } else {
                SELF = new RelatedAssetsGraph();
                SELF.init();
                Database.trySaveObject(SELF,FILE);
            }
        }
        return SELF;
    }

    public static void main(String[] args) {
        get();
    }
}
