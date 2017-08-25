package elasticsearch;

import com.google.gson.Gson;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import seeding.Constants;
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
        SimilarPatentServer.initialize(true,false);
        TransportClient client = MyClient.get();
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(DataIngester.INDEX_NAME);
        Collection<? extends AbstractAttribute> allAttributes = SimilarPatentServer.getAllTopLevelAttributes().stream().filter(attr->!(attr instanceof HiddenAttribute)).collect(Collectors.toList());

        Collection<? extends AbstractAttribute> childAttributes = allAttributes.stream().filter(attr->!Constants.FILING_ATTRIBUTES_SET.contains(attr.getName())).collect(Collectors.toList());
        Map<String,Object> childProperties = createPropertiesMap(childAttributes);

        builder = createMapping(builder, childProperties, DataIngester.TYPE_NAME, DataIngester.PARENT_TYPE_NAME);

        Collection<? extends AbstractAttribute> parentAttributes = allAttributes.stream().filter(attr->Constants.FILING_ATTRIBUTES_SET.contains(attr.getName())).collect(Collectors.toList());
        Map<String,Object> parentProperties = createPropertiesMap(parentAttributes);
        parentProperties.put("vector_obj",typeMap("object",null,null));
        builder = createMapping(builder, parentProperties, DataIngester.PARENT_TYPE_NAME, null);

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
                recursiveHelper(nestedAttr,nestedMapping);
            });
        } else {
            mapping.put(attr.getName(),typeMap(attr.getType(),null, attr.getNestedFields()));
        }
    }
}
