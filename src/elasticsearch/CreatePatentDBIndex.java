package elasticsearch;

import com.google.gson.Gson;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.LatestAssigneeAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.portfolios.PortfolioList;

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
        mapping.put("_parent", typeMap(DataIngester.TYPE_NAME,null,null));
        Collection<? extends AbstractAttribute> attributes = SimilarPatentServer.getAllAttributes().stream().filter(attr->!attr.getName().equals(Constants.LATEST_ASSIGNEE)).collect(Collectors.toList());
        LatestAssigneeAttribute latestAssignee = (LatestAssigneeAttribute) SimilarPatentServer.getAllAttributes().stream().filter(attr->attr.getName().equals(Constants.LATEST_ASSIGNEE)).findAny().orElse(null);
        attributes.forEach(attribute->{
            recursiveHelper(attribute, properties);
        });
        properties.put("vector_obj",typeMap("object",null,null));
        mapping.put("properties",properties);
        for(PortfolioList.Type type : PortfolioList.Type.values()) {
            builder.addMapping(type.toString(), mapping);
        }
        System.out.println("Query: " + new Gson().toJson(mapping));

        // assignee type
        Map<String,Object> assigneeProperties = new HashMap<>();
        Map<String,Object> assigneeMapping = new HashMap<>();
        recursiveHelper(latestAssignee,assigneeProperties);
        assigneeMapping.put("properties",assigneeProperties);
        builder.addMapping(latestAssignee.getName(), assigneeMapping);
        System.out.println("Query: " + new Gson().toJson(assigneeMapping));

        // filing type
        Map<String,Object> filingMapping = new HashMap<>();
        filingMapping.put("_parent", typeMap(latestAssignee.getName(),null,null));
        builder.addMapping(DataIngester.TYPE_NAME, filingMapping);
        System.out.println("Query: " + new Gson().toJson(filingMapping));

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
