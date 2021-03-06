package elasticsearch;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 7/25/2017.
 */
public class CreateDatasetIndex {
    public static void main(String[] args) {
        TransportClient client = MyClient.get();
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate(DatasetIndex.INDEX)
                .setSettings(Settings.builder()
                        .put("index.number_of_replicas",1)
                );

        Map<String,Object> mapping = new HashMap<>();
        Map<String,Object> properties = new HashMap<>();
        Map<String,Object> type = new HashMap<>();
        type.put("type","keyword");
        properties.put(DatasetIndex.DATA_FIELD, type);
        Map<String,Object> indexType = new HashMap<>();
        indexType.put("properties",properties);
        mapping.put(DatasetIndex.TYPE, indexType);
        builder.addMapping(DatasetIndex.TYPE, mapping);

        // get response
        builder.get();

        // set num replicas

    }
}
