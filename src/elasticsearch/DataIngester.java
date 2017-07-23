package elasticsearch;

import org.elasticsearch.action.index.IndexResponse;
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

    public static void ingestAsset(String name, PortfolioList.Type type, String text) {
        try {
            client.prepareIndex(INDEX_NAME, TYPE_NAME, name).setSource(
                    XContentFactory.jsonBuilder().startObject()
                            .field("pub_doc_number", name)
                            .field("tokens", text)
                            .field("doc_type", type.toString())
                    .endObject()
            ).get();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("ERROR ON: \n\tname: "+name+"\n\tType: "+type);
            throw new RuntimeException();
        }
    }
    public static void ingestItem(Item item, PortfolioList.Type type) {
        try {
            XContentBuilder json = XContentFactory.jsonBuilder().startObject()
                    .field("doc_type",type.toString());
            for(Map.Entry<String,Object> e : item.getDataMap().entrySet()) {
                json=json.field(e.getKey(),e.getValue());
            }
            json=json.endObject();
            client.prepareIndex(INDEX_NAME, TYPE_NAME, item.getName()).setSource(json).get();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("ERROR ON: "+item.getName());
            throw new RuntimeException();
        }
    }

}
