package cpc_normalization;

import elasticsearch.IngestMongoIntoElasticSearch;
import lombok.Getter;
import org.bson.Document;
import seeding.Constants;
import seeding.Database;
import seeding.google.mongo.IngestCPCDefinitions;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        return cpcWithAncestors(cpc);
    }

    public Collection<CPC> cpcWithAncestors(CPC cpc) {
        List<CPC> list = new ArrayList<>();
        while(cpc!=null) {
            list.add(cpc);
            cpc=cpc.getParent();
        }
        return list;
    }

    public void run() {
        topLevel = Collections.synchronizedCollection(new HashSet<>());
        labelToCPCMap = Collections.synchronizedMap(new HashMap<>());

        AtomicInteger i = new AtomicInteger(0);
        AtomicInteger connectionCounter = new AtomicInteger(0);
        Consumer<Document> consumer = doc -> {
            String id = doc.getString("_id");
            CPC cpc = labelToCPCMap.getOrDefault(id,new CPC(id));
            labelToCPCMap.putIfAbsent(id,cpc);
            if(i.getAndIncrement() % 10000==9999) {
                System.out.println("Completed "+i.get()+" cpcs.");
                System.out.println("Num connections: "+connectionCounter.get());
            }
            List<String> parents = (List<String>)doc.get("parents");
            if(parents!=null) {
                parents.forEach(parent->{
                    connectionCounter.getAndIncrement();
                    CPC parentCpc = labelToCPCMap.getOrDefault(parent,new CPC(parent));
                    labelToCPCMap.putIfAbsent(parent,parentCpc);
                    cpc.setParent(parentCpc);
                    parentCpc.addChild(cpc);
                });
            }
        };

        IngestMongoIntoElasticSearch.iterateOverCollection(consumer,new Document(), IngestCPCDefinitions.INDEX_NAME, IngestCPCDefinitions.TYPE_NAME,new String[]{});

        List<CPC> allNodes = new ArrayList<>(labelToCPCMap.values());

        AtomicInteger noParents = new AtomicInteger(0);
        allNodes.parallelStream().forEach(cpc->{
            if(cpc.getParent()==null&&cpc.getNumParts()>1) {
                noParents.getAndIncrement();
                System.out.println("NO PARENT FOR: " + cpc.toString());
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
        updateAndGetLatest();
    }

    public static CPCHierarchy updateAndGetLatest() {
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.run();
        hierarchy.save();
        return hierarchy;
    }
}
