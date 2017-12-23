package elasticsearch;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import user_interface.server.SimilarPatentServer;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 7/25/2017.
 */
public class CreateDatasetIndex {
    public static void main(String[] args) {
        TransportClient client = MyClient.get();
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(DatasetIndex.INDEX);

        Map<String,Object> mapping = new HashMap<>();
        Map<String,Object> properties = new HashMap<>();
        Map<String,Object> field = new HashMap<>();
        Map<String,Object> type = new HashMap<>();
        type.put("type","keyword");
        field.put(DatasetIndex.DATA_FIELD,type);
        properties.put("field", field);
        mapping.put("properties", properties);
        builder.addMapping(DatasetIndex.TYPE, mapping);

        // get response
        builder.get();
    }
}
