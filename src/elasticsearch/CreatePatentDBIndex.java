package elasticsearch;

import com.google.gson.Gson;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.hidden_attributes.HiddenAttribute;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/25/2017.
 */
public class CreatePatentDBIndex {
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true);
        TransportClient client = MyClient.get();
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(DataIngester.INDEX_NAME);
        Map<String,Object> mapping = new HashMap<>();
        Map<String,Object> properties = new HashMap<>();
        Collection<? extends AbstractAttribute> attributes = SimilarPatentServer.getAllAttributes().stream().filter(attr->!(attr instanceof HiddenAttribute)).collect(Collectors.toList());
        attributes.forEach(attribute->{
            recursiveHelper(attribute, properties);
        });
        properties.put("vector_obj",typeMap("object",null,null));
        mapping.put("properties",properties);
        mapping.put("_parent", typeMap(DataIngester.PARENT_TYPE_NAME,null,null));
        builder.addMapping(DataIngester.TYPE_NAME, mapping);
        System.out.println("Query: " + new Gson().toJson(mapping));

        // filings parent object
        Map<String,Object> parentMapping = new HashMap<>();
        builder.addMapping(DataIngester.PARENT_TYPE_NAME, parentMapping);

        // get response
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
