package elasticsearch;

import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataIngester {
    private static TransportClient client = MyClient.get();
    static final String INDEX_NAME = "ai_db";
    static final String TYPE_NAME = "patents_and_applications";

    public static void ingestAssets(Map<String,Map<String,Object>> labelToTextMap, boolean create) {
        try {
            BulkRequestBuilder request = client.prepareBulk();
            for (Map.Entry<String, Map<String,Object>> e : labelToTextMap.entrySet()) {
                XContentBuilder json = buildJson(e.getValue());
                request = request.add(client.prepareUpdate(INDEX_NAME, TYPE_NAME, e.getKey())
                        .setDoc(json));
            }
            BulkResponse response = request.get();
            if(create&&response.hasFailures()) {
                int numFailures = 0;
                request = client.prepareBulk();
                for(BulkItemResponse itemResponse : response.getItems()) {
                    if(itemResponse.isFailed()) {
                        numFailures++;
                        String id = itemResponse.getId();
                        XContentBuilder json = buildJson(labelToTextMap.get(id));
                        request = request.add(client.prepareIndex(INDEX_NAME, TYPE_NAME, id)
                                .setSource(json));
                    }
                }
                response = request.get();
                System.out.println("Update had failures: " + numFailures);
                if(response.hasFailures()) {
                    System.out.println("  And prepareIndex has failures.");
                } else {
                    System.out.println("  But were successfully indexed.");
                }
            }

        } catch (Exception e) {
            System.out.println("ERROR UPDATING BATCH");
            e.printStackTrace();
            System.out.println("ERROR UPDATING BATCH");
            //System.exit(1);
        } finally {
            labelToTextMap.clear();
        }
    }

    private static XContentBuilder buildJson(Map<String,Object> data) throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for(Map.Entry<String,Object> e : data.entrySet()) {
            builder = builder.field(e.getKey(),e.getValue());
        }
        builder = builder.endObject();
        return builder;
    }

    public static void ingestItems(Collection<Item> items, boolean create) {
        Map<String,Map<String,Object>> data = Collections.synchronizedMap(new HashMap<>(items.size()));
        items.parallelStream().forEach(item->{
            Map<String,Object> itemData = new HashMap<>();
            for(Map.Entry<String,Object> e : item.getDataMap().entrySet()) {
                itemData.put(e.getKey(), e.getValue());
            }
            data.put(item.getName(),itemData);
        });
        ingestAssets(data,create);
    }

}
