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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/24/2017.
 */
public class CPCHierarchy {
    private static final File cpcHierarchyTopLevelFile = new File(Constants.DATA_FOLDER+"cpc_toplevel_hierarchy.jobj");
    private static final File cpcHierarchyMapFile = new File(Constants.DATA_FOLDER+"cpc_map_hierarchy.jobj");
    @Getter
    protected Collection<CPC> topLevel;
    @Getter
    protected Map<String,CPC> labelToCPCMap;
    public CPCHierarchy() {
    }

    public Collection<CPC> cpcWithAncestors(String label) {
        CPC cpc = labelToCPCMap.get(label);
        List<CPC> list = new ArrayList<>();
        while(cpc!=null) {
            list.add(cpc);
            cpc=cpc.getParent();
        }
        return list;
    }

    public void run(Collection<String> allCPCs) {
        topLevel = Collections.synchronizedCollection(new HashSet<>());
        labelToCPCMap = Collections.synchronizedMap(new HashMap<>());

        AtomicInteger i = new AtomicInteger(0);
        Collection<CPC> allNodes = allCPCs.parallelStream().map(cpc->{
            if(cpc==null||cpc.length()==0) return null;
            CPC c = new CPC(cpc);
            if(i.getAndIncrement()%10000==9999) {
                System.out.println("Completed "+i.get()+" / "+allCPCs.size()+" cpcs.");
            }
            labelToCPCMap.put(cpc,c);
            return c;
        }).filter(n->n!=null).collect(Collectors.toList());

        i.set(0);
        RadixTree<CPC> prefixTrie = new ConcurrentRadixTree<>(new DefaultCharArrayNodeFactory());
        allNodes.forEach(node->{
            prefixTrie.put(new String(node.getName()),node);
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
                AtomicBoolean found = new AtomicBoolean(false);
                noParents.getAndIncrement();
                if(cpc.getNumParts()==5) {
                    String group = cpc.getParts()[3];
                    if (group != null && group.length() == 4) {
                        group = group.substring(1);
                        cpc.getParts()[3]=group;
                        while (group.length() > 0 && !found.get()) {
                            String name = String.join("", Arrays.copyOfRange(cpc.getParts(), 0, 4));
                            prefixTrie.getValuesForKeysStartingWith(name).forEach(n2->{
                                if (!cpc.equals(n2)) {
                                    if (n2.isParentOf(cpc)) {
                                        cpc.setParent(n2);
                                        n2.addChild(cpc);
                                        connectionCounter.getAndIncrement();
                                        found.set(true);
                                    }
                                }
                            });
                            if (group.length() == 1) group = "";
                            else group = group.substring(1);
                            cpc.getParts()[3] = group;
                        }
                    }
                }
                if(!found.get()) {
                    System.out.println("NO PARENT FOR: " + cpc.toString());
                } else {
                    noParents.getAndDecrement();
                }
            }
        });

        topLevel = allNodes.parallelStream().filter(n->n.getNumParts()==1)
                .collect(Collectors.toList());

        System.out.println("No parents for: "+noParents.get()+" / "+allNodes.size());
    }

    public void save() {
        Database.trySaveObject(topLevel,cpcHierarchyTopLevelFile);
        Database.trySaveObject(labelToCPCMap,cpcHierarchyMapFile);
    }

    public void loadGraph() {
        topLevel = (Collection<CPC> ) Database.tryLoadObject(cpcHierarchyTopLevelFile);
        labelToCPCMap = (Map<String,CPC>) Database.tryLoadObject(cpcHierarchyMapFile);
        labelToCPCMap.values().forEach(v->{
            if(v.getName()==null) throw new RuntimeException("Should not be null...");
        });
    }


    public static void main(String[] args) {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.run(new ArrayList<>(Database.getClassCodeToClassTitleMap().keySet()));
        hierarchy.save();
    }
}
