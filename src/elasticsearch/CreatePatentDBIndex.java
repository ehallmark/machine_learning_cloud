package elasticsearch;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;

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
        mapping.put("assignee",typeMap("keyword"));
        mapping.put("wipoTechnology",typeMap("keyword"));
        mapping.put("name",typeMap("keyword"));
        mapping.put("cpcTechnology",typeMap("keyword"));
        mapping.put("technologyValue",typeMap("keyword"));
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
