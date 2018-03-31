package seeding.google.elasticsearch;

import com.google.gson.Gson;
import elasticsearch.MyClient;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import seeding.google.attributes.Constants;
import seeding.google.mongo.ingest.IngestPatents;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.dataset_lookup.TermsLookupAttribute;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 7/25/2017.
 */
public class CreatePatentIndex {
    public static void main(String[] args) {
        TransportClient client = MyClient.get();
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(IngestPatents.INDEX_NAME)
                .setSettings(Settings.builder()
                        .put("index.number_of_replicas",0)
                );

        Collection<AbstractAttribute> allAttributes = Constants.buildAttributes();

        Map<String,Object> properties = createPropertiesMap(allAttributes);

        builder = createMapping(builder, properties, IngestPatents.TYPE_NAME, null);

        // get response
        builder.get();
    }

    private static CreateIndexRequestBuilder createMapping(CreateIndexRequestBuilder builder, Map<String,Object> properties, String typeName, String parentType) {
        Map<String,Object> mapping = new HashMap<>();
        mapping.put("properties",properties);
        if(parentType!=null) mapping.put("_parent", typeMap(parentType,null,null));
        builder.addMapping(typeName, mapping);
        System.out.println(typeName+" => Query: " + new Gson().toJson(mapping));
        return builder;
    }

    private static Map<String,Object> createPropertiesMap(Collection<? extends AbstractAttribute> attributes) {
        Map<String,Object> properties = new HashMap<>();
        attributes.forEach(attribute->{
            recursiveHelper(attribute, properties);
        });
        return properties;
    }

    private static Object typeMap(String type, Map<String,Object> props, Map<String,Object> nestedFields) {
        if(type==null&&props==null&&nestedFields==null) throw new RuntimeException("Not all fields can be null in typeMap");
        Map<String,Object> typeMap = new HashMap<>();
        if(type!=null) {
            typeMap.put("type",type);
        }
        if(props!=null) {
            typeMap.put("properties",props);
        }
        if(nestedFields!=null) {
            typeMap.put("fields", nestedFields);
        }
        return typeMap;
    }

    private static void recursiveHelper(AbstractAttribute attr, Map<String,Object> mapping) {
        if(attr instanceof NestedAttribute) {
            Map<String,Object> nestedMapping = new HashMap<>();
            if(attr.getType()!=null) {
                mapping.put(attr.getName(), typeMap(attr.getType(), nestedMapping, attr.getNestedFields()));
            }
            ((NestedAttribute) attr).getAttributes().forEach(nestedAttr->{
                recursiveHelper(nestedAttr,nestedMapping);
            });
        } else {
            if(attr.getType()!=null && !(attr instanceof TermsLookupAttribute)) {
                mapping.put(attr.getName(), typeMap(attr.getType(), null, attr.getNestedFields()));
            }
        }
    }
}
