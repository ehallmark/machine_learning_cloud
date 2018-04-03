package elasticsearch;

import com.google.gson.Gson;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

public class TestNewFastVectors {
    public static final int BYTES_PER_DOUBLE = 8;
    private static void createDatabase(TransportClient client) {
        CreateIndexRequestBuilder builder = client.admin().indices().prepareCreate("test")
                .setSettings(Settings.builder()
                        .put("index.number_of_replicas",1)
                );

        Map<String,Object> indexType = new HashMap<>();
        Map<String,Object> mapping = new HashMap<>();
        Map<String,Object> properties = new HashMap<>();
        Map<String,Object> type = new HashMap<>();
        type.put("doc_values", true);
        type.put("type","binary");
        properties.put("embedding_vector", type);
        indexType.put("properties",properties);
        mapping.put("type1", indexType);
        builder.addMapping("type1", mapping);

        // get response
        builder.get();

        // set num replicas
    }

    public static String vectorToHex(double[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length*BYTES_PER_DOUBLE);
        for(int i = 0; i < vector.length; i++) {
            buffer.putDouble(i*BYTES_PER_DOUBLE,vector[i]);
        }
        String encoding = Base64.getEncoder().encodeToString(buffer.array());
        System.out.println("Encoding: "+encoding);
        return encoding;
    }

    public static void main(String[] args) {
        TransportClient client = MyClient.get();

        //createDatabase(client);

        client.prepareIndex("test","type1","doc1")
                .setSource(Collections.singletonMap("embedding_vector", vectorToHex(new double[]{0.5,-0.5,0.25,-0.31,0.1})))
                .get();

        client.prepareIndex("test","type1","doc2")
                .setSource(Collections.singletonMap("embedding_vector", vectorToHex(new double[]{0.2,0.15,-0.5,0.1,-0.21})))
                .get();

        Map<String,Object> params = new HashMap<>();
        params.put("cosine",false);
        params.put("field","embedding_vector");
        params.put("vector", Arrays.asList(0.2d,0.15,-0.5,0.1,-0.21));
        SearchResponse response = client.prepareSearch("test").setTypes("type1").setSize(1)
                .setQuery(QueryBuilders.functionScoreQuery(
                        QueryBuilders.matchAllQuery(),
                        ScoreFunctionBuilders.scriptFunction(
                                new Script(ScriptType.INLINE,"knn","binary_vector_score", params)
                        )
                ).boostMode(CombineFunction.REPLACE)).get();

        Stream.of(response.getHits().getHits()).forEach(hit->{
            System.out.println("Hit: "+new Gson().toJson(hit.getSource()));
        });

    }
}
