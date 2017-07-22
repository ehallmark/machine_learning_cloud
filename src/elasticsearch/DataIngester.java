package elasticsearch;

import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Collection;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataIngester {
    private static TransportClient client = MyClient.get();
    private static XContentBuilder jsonBuilder;
    private static final String INDEX_NAME = "patentdb";
    private static final String TYPE_NAME = "patents_and_applications";
    
    public static void ingestAsset(String name, PortfolioList.Type type, String text) {
        try {
            IndexResponse response = client.prepareIndex(INDEX_NAME, TYPE_NAME, name).setSource(
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
}
