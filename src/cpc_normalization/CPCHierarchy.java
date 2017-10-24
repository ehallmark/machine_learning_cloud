package cpc_normalization;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharArrayNodeFactory;
import lombok.Getter;
import model.graphs.BayesianNet;
import model.graphs.Graph;
import model.nodes.Node;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/24/2017.
 */
public class CPCHierarchy {
    private static final File cpcHierarchyTopLevelFile = new File(Constants.DATA_FOLDER+"cpc_toplevel_hierarchy.jobj");
    private static final File cpcHiearchyMapFile = new File(Constants.DATA_FOLDER+"cpc_map_hierarchy.jobj");
    @Getter
    protected Collection<CPC> topLevel;
    protected Map<String,CPC> labelToCPCMap;
    public CPCHierarchy() {
    }

    public void run(Collection<String> allCPCs) {
        topLevel = Collections.synchronizedCollection(new HashSet<>());
        labelToCPCMap = Collections.synchronizedMap(new HashMap<>());

        AtomicInteger i = new AtomicInteger(0);
        Collection<CPC> allNodes = allCPCs.parallelStream().map(cpc->{
            CPC c = new CPC(cpc);
            if(i.getAndIncrement()%10000==9999) {
                System.out.println("Completed "+i.get()+" / "+allCPCs.size()+" cpcs.");
            }
            labelToCPCMap.put(cpc,c);
            return c;
        }).collect(Collectors.toList());

        i.set(0);
        RadixTree<CPC> prefixTrie = new ConcurrentRadixTree<>(new DefaultCharArrayNodeFactory());
        allNodes.forEach(node->{
            prefixTrie.put(node.getName(),node);
        });

        AtomicInteger connectionCounter = new AtomicInteger(0);
        allNodes.parallelStream().forEach(n1->{
            if(i.getAndIncrement() % 10000==9999) {
                System.out.println("Completed "+i.get()+" cpcs.");
                System.out.println("Num connections: "+connectionCounter.get());
            }
            String name = String.join("", Stream.of(n1.getParts()).filter(p->p!=null).collect(Collectors.toList()));
            prefixTrie.getValuesForKeysStartingWith(name).forEach(n2->{
                if(!n1.equals(n2)) {
                    if(n1.isParentOf(n2)) {
                        n2.setParent(n1);
                        n1.addChild(n2);
                        connectionCounter.getAndIncrement();
                    }
                }
            });
        });

        AtomicInteger noParents = new AtomicInteger(0);
        allNodes.parallelStream().forEach(cpc->{
            if(cpc.getParent()==null) {
                noParents.getAndIncrement();
                //System.out.println("NO PARENT FOR: "+cpc.toString());
            }
        });

        System.out.println("No parents for: "+noParents.get()+" / "+allNodes.size());
    }

    public void save() {
        Database.trySaveObject(topLevel,cpcHierarchyTopLevelFile);
        Database.trySaveObject(labelToCPCMap,cpcHiearchyMapFile);
    }

    public void loadGraph() {
        topLevel = (Collection<CPC> ) Database.tryLoadObject(cpcHierarchyTopLevelFile);
        labelToCPCMap = (Map<String,CPC>) Database.tryLoadObject(cpcHiearchyMapFile);

    }


    public static void main(String[] args) {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.run(new ArrayList<>(Database.getClassCodeToClassTitleMap().keySet()));
        hierarchy.save();
    }
}
