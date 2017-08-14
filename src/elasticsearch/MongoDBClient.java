package elasticsearch;

import com.mongodb.WriteConcern;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoClients;
import com.mongodb.connection.ConnectionPoolSettings;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Evan on 7/22/2017.
 */
public class MongoDBClient {
    private static MongoClient CLIENT;
    private MongoDBClient() {}
    private static void init() {
        MongoClient fake = MongoClients.create();
        MongoClientSettings settings = MongoClientSettings.builder(fake.getSettings())
                .writeConcern(WriteConcern.ACKNOWLEDGED)
                .connectionPoolSettings(
                        ConnectionPoolSettings.builder(fake.getSettings().getConnectionPoolSettings())
                                .maxSize(400)
                                .build()
                ).build();
        CLIENT = MongoClients.create(settings);
    }


    public synchronized static MongoClient get() {
        if(CLIENT==null) {
            try {
                init();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return CLIENT;
    }

    public synchronized static void close() {
        if(CLIENT!=null) {
            CLIENT.close();
        }
    }

    public static void main(String[] args) throws Exception {
        // test
        get().getDatabase("ai_db").getCollection("patents_and_applications").count((res,t)->System.out.println("Count: "+res.doubleValue()));
        TimeUnit.SECONDS.sleep(10);
        get().close();
        System.out.println("Database finished");
    }
}
