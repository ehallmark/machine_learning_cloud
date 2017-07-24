package elasticsearch;

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
import java.util.Map;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataIngester {
    private static TransportClient client = MyClient.get();
    static final String INDEX_NAME = "patentdb";
    static final String TYPE_NAME = "patents_and_applications";

    public static void ingestAssets(Map<String,String> labelToTextMap, PortfolioList.Type type) {
        try {
            BulkRequestBuilder request = client.prepareBulk();
            for (Map.Entry<String, String> e : labelToTextMap.entrySet()) {
                XContentBuilder json = XContentFactory.jsonBuilder().startObject()
                        .field("pub_doc_number", e.getKey())
                        .field("doc_type", type.toString())
                        .field("tokens", e.getValue())
                        .endObject();
                request = request.add(client.prepareIndex(INDEX_NAME, TYPE_NAME, e.getKey())
                        .setSource(json));
            }
            BulkResponse response = request.get();
            if(!response.hasFailures()) {
                labelToTextMap.clear();
            }
            System.out.println("Update had failures: " + response.hasFailures());

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR UPDATING BATCH");
            throw new RuntimeException();
        }
    }
    public static void ingestItems(Collection<Item> items, PortfolioList.Type type) {
        try {
            BulkRequestBuilder request = client.prepareBulk();
            for(Item item : items) {
                XContentBuilder json = XContentFactory.jsonBuilder().startObject()
                        .field("doc_type",type.toString());
                for(Map.Entry<String,Object> e : item.getDataMap().entrySet()) {
                    json=json.field(e.getKey(),e.getValue());
                }
                json=json.endObject();
                request = request.add(client.prepareUpdate(INDEX_NAME,TYPE_NAME,item.getName())
                        .setDoc(json));
            }
            BulkResponse response = request.get();
            System.out.println("Update had failures: "+response.hasFailures());

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("ERROR UPDATING BATCH");
            throw new RuntimeException();
        }
    }

}
