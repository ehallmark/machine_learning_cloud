package elasticsearch;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import seeding.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 7/25/2017.
 */
public class CreatePatentDBIndex {
    public static void main(String[] args) {
        TransportClient client = MyClient.get();
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(DataIngester.INDEX_NAME);
        Map<String,Object> mapping = new HashMap<>();
        mapping.put("doc_type",typeMap("keyword"));
        mapping.put("pub_doc_number",typeMap("keyword"));
        mapping.put("tokens",typeMap("text"));
        mapping.put(Constants.ASSIGNEE,typeMap("keyword"));
        mapping.put(Constants.WIPO_TECHNOLOGY,typeMap("keyword"));
        mapping.put(Constants.NAME,typeMap("keyword"));
        mapping.put(Constants.CPC_TECHNOLOGY,typeMap("keyword"));
        mapping.put(Constants.CPC_CODES,typeMap("keyword"));
        mapping.put(Constants.TECHNOLOGY,typeMap("keyword"));
        mapping.put("vector_obj",typeMap("object"));
        mapping.put(Constants.ASSIGNEE_ENTITY_TYPE,typeMap("keyword"));
        Map<String,Object> properties = new HashMap<>();
        properties.put("properties",mapping);
        builder.addMapping(DataIngester.TYPE_NAME, properties);
        System.out.println("Query: "+builder.toString());
        builder.get();
    }

    private static Object typeMap(String type) {
        Map<String,String> typeMap = new HashMap<>();
        typeMap.put("type",type);
        return typeMap;
    }
}
