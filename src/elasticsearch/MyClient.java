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
        boolean sniff = false;
        int port = 9300;
        InetAddress host;
        try {
            host = InetAddress.getByName("localhost");
        } catch (UnknownHostException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to find local host");
        }
        Settings settings = Settings.builder()
                .put("client.transport.sniff", sniff).build();

        TransportClient client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(host,port));
        CLIENT=client;
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
