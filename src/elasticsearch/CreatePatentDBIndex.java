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
        Map<String,String> mapping = new HashMap<>();
        mapping.put("doc_type","keyword");
        mapping.put("pub_doc_number","keyword");
        mapping.put("tokens","text");
        mapping.put("assignee","keyword");
        mapping.put("wipoTechnology","keyword");
        mapping.put("name","keyword");
        mapping.put("cpcTechnology","keyword");
        mapping.put("technologyValue","keyword");
        Map<String,Object> properties = new HashMap<>();
        properties.put("properties",mapping);
        builder.addMapping(DataIngester.TYPE_NAME, properties);
        builder.get();
    }
}
