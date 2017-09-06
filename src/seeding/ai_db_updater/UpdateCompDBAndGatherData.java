package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.CompDBNestedAttribute;
import user_interface.ui_models.attributes.GatherNestedAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 7/23/2017.
 */
public class UpdateCompDBAndGatherData {
    static AssetToFilingMap assetToFilingMap;

    public static void main(String[] args) {
        assetToFilingMap = new AssetToFilingMap();

        Collection<AbstractAttribute> gatherAttributes = Arrays.asList(new GatherNestedAttribute());
        Collection<String> gatherAssets = Database.getGatherAssets();

        System.out.println("Starting to update gather data...");
        ingestAttributesForAssets(gatherAttributes,gatherAssets);
        DataIngester.finishCurrentMongoBatch();

        Collection<AbstractAttribute> compDBAttributes = Arrays.asList(new CompDBNestedAttribute());
        Collection<String> compDBAssets = Database.getCompDBAssets();

        System.out.println("Starting to update compdb data...");
        ingestAttributesForAssets(compDBAttributes,compDBAssets);
        DataIngester.finishCurrentMongoBatch();

        System.out.println("Starting to ingest into elasticsearch...");
        // add to elastic search
        Map<String,Object> idMap = new HashMap<>();
        idMap.put("$in",union(gatherAssets,compDBAssets).stream().map(asset->assetToFilingMap.getApplicationDataMap().getOrDefault(asset,assetToFilingMap.getPatentDataMap().get(asset))).filter(filing->filing!=null).distinct().collect(Collectors.toList()));
        Document query = new Document("_id", idMap);
        IngestMongoIntoElasticSearch.ingestByType(DataIngester.PARENT_TYPE_NAME,query);

        DataIngester.close();
    }

    private static List<String> union(Collection<String> c1, Collection<String> c2) {
        List<String> c3 = Stream.of(c1,c2).flatMap(c->c.stream()).distinct().collect(Collectors.toList());
        return c3;
    }

    private static void ingestAttributesForAssets(Collection<AbstractAttribute> attributes, Collection<String> assets) {
        assets.parallelStream().forEach(asset->{
            String filing = assetToFilingMap.getApplicationDataMap().getOrDefault(asset,assetToFilingMap.getPatentDataMap().get(asset));
            if(filing == null) return;
            Map<String,Object> data = new HashMap<>();
            attributes.forEach(attr->helper(attr,data,asset));
            if(data.isEmpty()) return;
            DataIngester.ingestBulk(asset,filing,data,false);
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
                Object value = ((ComputableAttribute) attribute).attributesFor(Arrays.asList(asset),1);
                if(value!=null) {
                    data.put(attribute.getName(),value);
                }
            }
        }
    }
}
