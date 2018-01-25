package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.GatherNestedAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateCompDBAndGatherData {
    public static List<String> update() {
        Database.main(null);
        try {
            Database.loadCompDBData();
        } catch(Exception e) {
            e.printStackTrace();
        }

        Collection<AbstractAttribute> gatherAttributes = Arrays.asList(new GatherNestedAttribute());
        Collection<String> gatherAssets = Database.getGatherAssets();

        System.out.println("Starting to update gather data...");
        ingestAttributesForAssets(gatherAttributes,gatherAssets);
        DataIngester.finishCurrentMongoBatch();

        Collection<String> compDBAssets = Database.getCompDBAssets();
        System.out.println("Starting to update compdb data...");
        ingestCompDBNestedData(compDBAssets);
        DataIngester.finishCurrentMongoBatch();

        {
            System.out.println("Starting to ingest into elasticsearch...");
            // add to elastic search
            Map<String, Object> idMap = new HashMap<>();
            idMap.put("$in", union(gatherAssets, compDBAssets).stream().filter(filing -> filing != null).distinct().collect(Collectors.toList()));
            Document query = new Document("_id", idMap);
            IngestMongoIntoElasticSearch.ingestByType(DataIngester.TYPE_NAME, query);
        }
        Database.main(null);
        return Stream.of(gatherAssets,compDBAssets).flatMap(list->list.stream()).collect(Collectors.toList());
    }

    private static List<String> union(Collection<String> c1, Collection<String> c2) {
        List<String> c3 = Stream.of(c1,c2).flatMap(c->c.stream()).distinct().collect(Collectors.toList());
        return c3;
    }

    private static void ingestCompDBNestedData(Collection<String> assets) {
        Map<String,List<Map<String,Object>>> compDBData = Database.getCompDBAssetToNestedDataMap();
        assets.parallelStream().forEach(asset->{
            Map<String,Object> data = new HashMap<>();
            List<Map<String,Object>> compdb = compDBData.get(asset);
            if(compdb!=null&&compdb.size()>0) {
                data.put(Constants.COMPDB, compdb);
                DataIngester.ingestBulk(asset, data, false);
            }
        });
    }

    private static void ingestAttributesForAssets(Collection<AbstractAttribute> attributes, Collection<String> assets) {
        assets.parallelStream().forEach(asset->{
            Map<String,Object> data = new HashMap<>();
            attributes.forEach(attr->helper(attr,data,asset));
            if(data.isEmpty()) return;
            DataIngester.ingestBulk(asset,data,false);
        });
    }

    private static void helper(AbstractAttribute attribute, Map<String,Object> data, String asset) {
        if(attribute instanceof NestedAttribute) {
            Map<String,Object> innerMap = new HashMap<>();
            data.put(attribute.getName(),innerMap);
            ((NestedAttribute) attribute).getAttributes().forEach(childAttr->{
                helper(childAttr,innerMap,asset);
            });
        } else {
            if(attribute instanceof ComputableAttribute) {
                Object value = ((ComputableAttribute) attribute).attributesFor(Arrays.asList(asset),1, null);
                if(value!=null) {
                    data.put(attribute.getName(),value);
                }
            }
        }
    }
}
