package elasticsearch;

import com.google.gson.Gson;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 7/25/2017.
 */
public class CreatePatentDBIndex {
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true);
        TransportClient client = MyClient.get();
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(DataIngester.INDEX_NAME);
        Map<String,Object> mapping = new HashMap<>();
        Collection<? extends AbstractAttribute> attributes = SimilarPatentServer.getAllAttributes();
        attributes.forEach(attribute->{
            recursiveHelper(attribute, mapping);
        });
        mapping.put("vector_obj",typeMap("object",null,null));
        Map<String,Object> properties = new HashMap<>();
        properties.put("properties",mapping);
        builder.addMapping(DataIngester.TYPE_NAME, properties);
        System.out.println("Query: "+new Gson().toJson(properties));
        builder.get();
    }

    private static Object typeMap(String type, Map<String,Object> props, Map<String,Object> nestedFields) {
        Map<String,Object> typeMap = new HashMap<>();
        typeMap.put("type",type);
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
            mapping.put(attr.getName(),typeMap(attr.getType(),nestedMapping,attr.getNestedFields()));
            ((NestedAttribute) attr).getAttributes().forEach(nestedAttr->{
                recursiveHelper((AbstractAttribute)nestedAttr,nestedMapping);
            });
        } else {
            mapping.put(attr.getName(),typeMap(attr.getType(),null, attr.getNestedFields()));
        }
    }
}
