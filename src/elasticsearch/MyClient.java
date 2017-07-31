package elasticsearch;

import lombok.Getter;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by Evan on 7/22/2017.
 */
public class MyClient {
    private static TransportClient CLIENT;
    private MyClient() {}
    private static void init() {
        boolean sniff = true;
        int port = 9300;
        try {
            Settings settings = Settings.builder()
                    .put("cluster.name", "docker-cluster")
                    .put("client.transport.sniff", sniff).build();

            TransportClient client = new PreBuiltTransportClient(settings)
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300))
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9301));
            CLIENT = client;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to find host");
        }
    }

    public static TransportClient get() {
        if(CLIENT==null) {
            init();
        }
        return CLIENT;
    }

    public static void main(String[] args) {
        TransportClient client = MyClient.get();
        GetResponse response = client.prepareGet("documents","asset","20018332334")
                .get();
        System.out.println(response.getSource().get("number")+": "+response.getSource().get("body"));


    }
}
