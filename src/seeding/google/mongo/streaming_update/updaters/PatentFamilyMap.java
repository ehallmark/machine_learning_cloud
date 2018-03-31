package seeding.google.mongo.streaming_update.updaters;

import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.ingest.IngestPatents;
import seeding.google.mongo.streaming_update.StreamableUpdater;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public class PatentFamilyMap implements StreamableUpdater {
    private static Map<String,Set<String>> familyIDToAssetsMap;
    private static Map<String,String> assetToFamilyIDMap;
    private static final File familyIDToAssetsMapFile = new File(seeding.Constants.DATA_FOLDER+StreamableUpdater.UPDATE_FOLDER+"family_id_to_assets_map.jobj");
    private static final File assetToFamilyIDMapFile = new File(seeding.Constants.DATA_FOLDER+StreamableUpdater.UPDATE_FOLDER+"asset_to_family_id_map.jobj");

    public static synchronized Map<String,Set<String>> getOrLoadFamilyIDToAssetsMap() {
        if(familyIDToAssetsMap==null) {
            familyIDToAssetsMap = (Map<String,Set<String>>)Database.tryLoadObject(familyIDToAssetsMapFile);
        }
        return familyIDToAssetsMap;
    }

    public static synchronized Map<String,String> getOrLoadAssetToFamilyIDMap() {
        if(assetToFamilyIDMap==null) {
            assetToFamilyIDMap = (Map<String,String>)Database.tryLoadObject(assetToFamilyIDMapFile);
        }
        return assetToFamilyIDMap;
    }


    @Override
    public List<String> getFields() {
        return Arrays.asList(
                Constants.PUBLICATION_NUMBER_WITH_COUNTRY,
                Constants.FAMILY_ID
        );
    }

    @Override
    public void updateDocument(Document doc, Map<String, Object> set, Map<String, Object> unset) {
        // do nothing...
    }

    @Override
    public Consumer<Document> getConsumer() {
        if(assetToFamilyIDMap == null) {
            assetToFamilyIDMap = Collections.synchronizedMap(new HashMap<>());
        }
        return doc -> {
            String familyID = doc.getString(Constants.FAMILY_ID);
            String pubNumber = doc.getString(Constants.PUBLICATION_NUMBER_WITH_COUNTRY);
            if(familyID!=null&&pubNumber!=null) {
                assetToFamilyIDMap.put(pubNumber,familyID);
            } else {
                System.out.println("Missing family id");
            }
        };
    }

    @Override
    public void finish() {
        familyIDToAssetsMap = Collections.synchronizedMap(new HashMap<>());
        assetToFamilyIDMap.forEach((pubNumber,familyID)->{
            familyIDToAssetsMap.putIfAbsent(familyID,Collections.synchronizedSet(new HashSet<>()));
            familyIDToAssetsMap.get(familyID).add(pubNumber);
        });
        Database.trySaveObject(familyIDToAssetsMap,familyIDToAssetsMapFile);
        Database.trySaveObject(assetToFamilyIDMap,assetToFamilyIDMapFile);
    }
}
