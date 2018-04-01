package seeding.google.mongo.streaming_update.updaters;

import cpc_normalization.CPCHierarchy;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.streaming_update.StreamableUpdater;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CPCMap implements StreamableUpdater {
    private static Map<String,Set<String>> cpcToAssetsMap;
    private static Map<String,Set<String>> assetToCPCMap;
    private static final File cpcToAssetsMapFile = new File(seeding.Constants.DATA_FOLDER+StreamableUpdater.UPDATE_FOLDER+"cpc_to_assets_map.jobj");
    private static final File assetToCPCMapFile = new File(seeding.Constants.DATA_FOLDER+StreamableUpdater.UPDATE_FOLDER+"asset_to_cpc_map.jobj");
    private static CPCHierarchy hierarchy = CPCHierarchy.get();
    public static synchronized Map<String,Set<String>> getOrLoadCPCToAssetsMap() {
        if(cpcToAssetsMap==null) {
            cpcToAssetsMap = (Map<String,Set<String>>)Database.tryLoadObject(cpcToAssetsMapFile);
        }
        return cpcToAssetsMap;
    }

    public static synchronized Map<String,Set<String>> getOrLoadAssetToCPCMap() {
        if(assetToCPCMap==null) {
            assetToCPCMap = (Map<String,Set<String>>)Database.tryLoadObject(assetToCPCMapFile);
        }
        return assetToCPCMap;
    }


    @Override
    public List<String> getFields() {
        return Arrays.asList(
                Constants.CPC,
                Constants.PUBLICATION_NUMBER_WITH_COUNTRY
        );
    }

    @Override
    public Consumer<Document> getConsumer() {
        if(assetToCPCMap == null) {
            assetToCPCMap = Collections.synchronizedMap(new HashMap<>());
        }
        return doc -> {
            List<Map<String,Object>> cpcs = (List<Map<String,Object>>)doc.get(Constants.CPC);
            String pubNumber = doc.getString(Constants.PUBLICATION_NUMBER_WITH_COUNTRY);
            if(cpcs!=null&&pubNumber!=null) {
                Set<String> cpcSet = Collections.synchronizedSet(cpcs.stream().map(map->{
                    String cpc = (String)map.get(Constants.CODE);
                    return cpc;
                }).filter(cpc->cpc!=null).collect(Collectors.toSet()));
                        assetToCPCMap.put(pubNumber,cpcSet);
            } else {
                System.out.println("Missing cpc");
            }
        };
    }

    @Override
    public void updateDocument(Document doc, Map<String, Object> set, Map<String, Object> unset) {
        // do nothing
    }

    @Override
    public void finish() {
        cpcToAssetsMap = Collections.synchronizedMap(new HashMap<>());
        assetToCPCMap.forEach((pubNumber,cpcs)->{
            cpcs.forEach(cpc-> {
                cpcToAssetsMap.putIfAbsent(cpc, Collections.synchronizedSet(new HashSet<>()));
                cpcToAssetsMap.get(cpc).add(pubNumber);
            });
        });
        Database.trySaveObject(cpcToAssetsMap,cpcToAssetsMapFile);
        Database.trySaveObject(assetToCPCMap,assetToCPCMapFile);
    }
}
