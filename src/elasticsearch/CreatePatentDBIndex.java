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
        SimilarPatentServer.initialize(false,true);
        TransportClient client = MyClient.get();
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(DataIngester.INDEX_NAME);
        Map<String,Object> mapping = new HashMap<>();
        Collection<? extends AbstractAttribute> attributes = SimilarPatentServer.getAllAttributes();
        attributes.forEach(attribute->{
            recursiveHelper(attribute, mapping);
        });
        mapping.put("vector_obj",typeMap("object"));
        Map<String,Object> properties = new HashMap<>();
        properties.put("properties",mapping);
        builder.addMapping(DataIngester.TYPE_NAME, properties);
        System.out.println("Query: "+new Gson().toJson(properties));
       // builder.get();
    }

    private static Object typeMap(String type) {
        Map<String,String> typeMap = new HashMap<>();
        typeMap.put("type",type);
        return typeMap;
    }

    private static void recursiveHelper(AbstractAttribute attr, Map<String,Object> mapping) {
        if(attr instanceof NestedAttribute) {
            Map<String,Object> nestedMapping = new HashMap<>();
            mapping.put(attr.getName(),nestedMapping);
            ((NestedAttribute) attr).getAttributes().forEach(nestedAttr->{
                recursiveHelper((AbstractAttribute)nestedAttr,nestedMapping);
            });
        } else {
            // stop
            mapping.put(attr.getName(),typeMap(attr.getType()));
        }
    }
}
