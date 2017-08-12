package elasticsearch;

import lombok.Getter;
import org.elasticsearch.action.bulk.BackoffPolicy;
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

/**
 * Created by Evan on 7/22/2017.
 */
public class MyClient {
    private static TransportClient CLIENT;
    private static BulkProcessor BULK_PROCESSOR;
    private MyClient() {}
    private static void init() {
        boolean sniff = false;
        try {
            Settings settings = Settings.builder()
                    .put("client.transport.sniff", sniff)
                    .put("cluster.name","elasticsearch").build();
            TransportClient client = new PreBuiltTransportClient(settings)
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300))
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9301))
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9302));
            CLIENT = client;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to find host");
        }
    }

    public synchronized static TransportClient get() {
        if(CLIENT==null) {
            init();
        }
        return CLIENT;
    }

    public synchronized static BulkProcessor getBulkProcessor() {
        if(BULK_PROCESSOR==null) {
            BULK_PROCESSOR = BulkProcessor.builder(get(), new BulkProcessor.Listener() {
                @Override
                public void beforeBulk(long l, BulkRequest bulkRequest) {

                }

                @Override
                public void afterBulk(long l, BulkRequest bulkRequest, BulkResponse bulkResponse) {

                }

                @Override
                public void afterBulk(long l, BulkRequest bulkRequest, Throwable throwable) {

                }
            })
                    .setBulkActions(10000)
                    .setBulkSize(new ByteSizeValue(10, ByteSizeUnit.MB))
                    .setFlushInterval(TimeValue.timeValueSeconds(10))
                    .setConcurrentRequests(Runtime.getRuntime().availableProcessors()/2+1)
                    .build();
        }
        return BULK_PROCESSOR;
    }

    public static void main(String[] args) {
        TransportClient client = MyClient.get();
        GetResponse response = client.prepareGet("documents","asset","20018332334")
                .get();
        System.out.println(response.getSource().get("number")+": "+response.getSource().get("body"));


    }
}
