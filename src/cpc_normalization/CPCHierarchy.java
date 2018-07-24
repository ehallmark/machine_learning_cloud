package cpc_normalization;

import data_pipeline.helpers.Function2;
import lombok.Getter;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 10/24/2017.
 */
public class CPCHierarchy {
    private static CPCHierarchy MODEL;
    private static final File cpcHierarchyTopLevelFile = new File(Constants.DATA_FOLDER+"cpc_toplevel_hierarchy.jobj");
    private static final File cpcHierarchyMapFile = new File(Constants.DATA_FOLDER+"cpc_map_hierarchy.jobj");
    @Getter
    protected Collection<CPC> topLevel;
    @Getter
    protected Map<String,CPC> labelToCPCMap;
    public CPCHierarchy() {
        if(MODEL!=null) {
            topLevel=MODEL.topLevel;
            labelToCPCMap=MODEL.labelToCPCMap;
        }
    }

    public static synchronized CPCHierarchy get() {
        if(MODEL == null) {
            MODEL = new CPCHierarchy();
            MODEL.loadGraph();
        }
        return MODEL;
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

    public void run() throws SQLException {
        topLevel = Collections.synchronizedCollection(new HashSet<>());
        labelToCPCMap = Collections.synchronizedMap(new HashMap<>());

        AtomicInteger i = new AtomicInteger(0);
        AtomicInteger connectionCounter = new AtomicInteger(0);
        Function2<String,List<String>,Void> consumer = (id, parents) -> {
            CPC cpc = labelToCPCMap.getOrDefault(id,new CPC(id));
            labelToCPCMap.putIfAbsent(id,cpc);
            if(i.getAndIncrement() % 10000==9999) {
                System.out.println("Completed "+i.get()+" cpcs.");
                System.out.println("Num connections: "+connectionCounter.get());
            }
            if(parents!=null) {
                parents.stream().sorted((p1,p2)->Integer.compare(p2.length(),p1.length())).limit(1).forEach(parent->{
                    connectionCounter.getAndIncrement();
                    CPC parentCpc = labelToCPCMap.getOrDefault(parent,new CPC(parent));
                    labelToCPCMap.putIfAbsent(parent,parentCpc);
                    cpc.setParent(parentCpc);
                    parentCpc.addChild(cpc);
                });
            }
            return null;
        };

        PreparedStatement ps = Database.getConn().prepareStatement("select code,parents from big_query_cpc_definition");
        ResultSet rs = ps.executeQuery();
        while(rs.next()) {
            String id = rs.getString(1);
            Array parentArray = rs.getArray(2);
            List<String> parents;
            if(parentArray == null) {
                parents = null;
            } else {
                parents = Arrays.asList((String[])parentArray.getArray());
            }
            consumer.apply(id, parents);
        }
        rs.close();
        ps.close();
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
        if(topLevel==null) {
            topLevel = (Collection<CPC>) Database.tryLoadObject(cpcHierarchyTopLevelFile);
        }
        if(labelToCPCMap==null) {
            labelToCPCMap = (Map<String, CPC>) Database.tryLoadObject(cpcHierarchyMapFile);
            labelToCPCMap.values().forEach(v -> {
                if (v.getName() == null) throw new RuntimeException("Should not be null...");
            });
        }
    }


    public static void main(String[] args) {
        updateAndGetLatest();
    }

    public static CPCHierarchy updateAndGetLatest() {
        CPCHierarchy hierarchy = new CPCHierarchy();
        try {
            hierarchy.run();
            hierarchy.save();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return hierarchy;
    }
}
