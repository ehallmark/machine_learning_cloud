package cpc_normalization;

import lombok.Getter;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.nodes.Node;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 10/24/2017.
 */
public class CPCHierarchy {
    private static final File cpcHierarchyFile = new File(Constants.DATA_FOLDER+"cpc_hierarchy.jobj");
    @Getter
    protected BayesianNet graph;
    public CPCHierarchy() {
    }

    public void run(Collection<String> allCPCs) {
        graph = new BayesianNet();
        Collection<Node> allNodes = allCPCs.parallelStream().map(cpc->{
            return graph.addBinaryNode(cpc);
        }).collect(Collectors.toList());

        AtomicInteger i = new AtomicInteger(0);
        allNodes.parallelStream().forEach(n1->{
            allNodes.forEach(n2->{
                if(!n1.equals(n2)) {
                    if(isParentOf(n1.getLabel(),n2.getLabel())) {
                        graph.connectNodes(n1,n2);
                        if(i.getAndIncrement() % 10000==9999) {
                            System.out.println("Completed "+i.get()+" cpcs.");
                        }
                    }
                }
            });
        });
    }

    public void save() {
        Database.trySaveObject(graph,cpcHierarchyFile);
    }

    public void loadGraph() {
        graph = (BayesianNet) Database.tryLoadObject(cpcHierarchyFile);
    }

    private static String[] cpcToParts(String cpc) {
        String[] parts = new String[5];
        if(cpc.length()>=1) parts[0] = cpc.substring(0,1);
        if(cpc.length()>=3) parts[1] = cpc.substring(1,3);
        if(cpc.length()>=4) parts[2] = cpc.substring(3,4);
        if(cpc.length()>4) {
            String groupStr = cpc.substring(4);
            if(groupStr.endsWith("/00")) groupStr = groupStr.substring(0,groupStr.length()-3); // main group
            String[] groups = groupStr.split("/");
            for(int i = 0; i < groups.length; i++) {
                parts[3+i] = groups[i].trim();
            }
        }
        return parts;
    }

    private static boolean isParentOf(String parent, String child) {
        String[] parentParts = cpcToParts(parent);
        String[] childParts = cpcToParts(child);

        if(parent.length()>=child.length()) return false;

        boolean same = true;
        for(int i = 0; i < 5; i++) {
            if(parentParts[i]==null||childParts[i]==null) break;
            if(!parentParts[i].equals(childParts[i])) {
                same = false;
                break;
            }
        }

        return same;
    }

    public static void main(String[] args) {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.run(Database.getClassCodes());
        hierarchy.save();
    }
}
