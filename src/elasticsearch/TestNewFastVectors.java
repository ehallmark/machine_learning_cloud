package elasticsearch;

import com.google.gson.Gson;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import seeding.google.elasticsearch.Attributes;
import seeding.google.mongo.ingest.IngestPatents;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Stream;

public class TestNewFastVectors {
    public static final int BYTES_PER_DOUBLE = 8;
    public static final int BYTES_PER_FLOAT = 4;
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
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    public static String vectorToHex(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length*BYTES_PER_FLOAT);
        for(int i = 0; i < vector.length; i++) {
            buffer.putFloat(i*BYTES_PER_FLOAT,vector[i]);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }


    public static void main(String[] args) {
        TransportClient client = MyClient.get();

        try {
            createDatabase(client);
        } catch(Exception e) {
            System.out.println("Error creating database: "+e.getMessage());
        }

        client.prepareIndex("test","type1","doc1")
                .setSource(Collections.singletonMap("embedding_vector", vectorToHex(new float[]{-0.2f,-0.15f,0.5f,-0.1f,0.21f})))
                .get();

        client.prepareIndex("test","type1","doc2")
                .setSource(Collections.singletonMap("embedding_vector", vectorToHex(new float[]{0.2f,0.15f,-0.5f,0.1f,-0.21f})))
                .get();

        Map<String,Object> params = new HashMap<>();
        params.put("cosine",true);
        params.put("field","embedding_vector");
        params.put("vector", Arrays.asList(0.2d,0.15,-0.5,0.1));
        params.put("float",true);
        params.put("scale", 100D);
        SearchResponse response = client.prepareSearch("test").setTypes("type1").setSize(2)
                .setTrackScores(true)
                .addSort(SortBuilders.scriptSort(new Script(ScriptType.INLINE,"knn","binary_vector_score", params), ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.DESC))
                .addScriptField("dot", new Script(ScriptType.INLINE,"knn","binary_vector_score", params))
                .setQuery(QueryBuilders.functionScoreQuery(
                                QueryBuilders.matchAllQuery(),
                                ScoreFunctionBuilders.scriptFunction(
                                        new Script(ScriptType.INLINE,"knn","binary_vector_score", params)
                                )
                        ).boostMode(CombineFunction.SUM).scoreMode(FiltersFunctionScoreQuery.ScoreMode.SUM)
                ).get();

        Stream.of(response.getHits().getHits()).forEach(hit->{
            System.out.println("Hit "+hit.getId()+": "+new Gson().toJson(hit.getSource()));
            System.out.println("Fields "+hit.getId()+": "+new Gson().toJson(hit.getFields()));
            System.out.println("Score: "+hit.getScore());
        });

        System.out.println("Patent test 1...");
        params.put("field", Attributes.ENC);
        response = client.prepareSearch(IngestPatents.INDEX_NAME).setTypes(IngestPatents.TYPE_NAME).setSize(2)
                //.setTrackScores(true)
                .addSort(SortBuilders.scriptSort(new Script(ScriptType.INLINE,"knn","binary_vector_score", params), ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.DESC))
                .addScriptField(Attributes.SIMILARITY, new Script(ScriptType.INLINE,"knn","binary_vector_score", params))
                .setQuery(
                        QueryBuilders.boolQuery()
                            .must(QueryBuilders.functionScoreQuery(
                                    QueryBuilders.matchAllQuery(),
                                    ScoreFunctionBuilders.scriptFunction(new Script(ScriptType.INLINE,"knn","binary_vector_score", params))).boostMode(CombineFunction.SUM).scoreMode(FiltersFunctionScoreQuery.ScoreMode.SUM)
                            ).must(
                                    QueryBuilders.existsQuery(Attributes.ENC)
                        )
                ).get();
        Stream.of(response.getHits().getHits()).forEach(hit->{
            System.out.println("Hit "+hit.getId()+": "+new Gson().toJson(hit.getSource()));
            System.out.println("Fields "+hit.getId()+": "+new Gson().toJson(hit.getFields()));
            System.out.println("Score: "+hit.getScore());
        });


        System.out.println("Patent test 2...");
        params.put("field", Attributes.ENC);
        params.put("float", false);
        response = client.prepareSearch(IngestPatents.INDEX_NAME).setTypes(IngestPatents.TYPE_NAME).setSize(2)
                //.setTrackScores(true)
                .addSort(SortBuilders.scriptSort(new Script(ScriptType.INLINE,"knn","binary_vector_score", params), ScriptSortBuilder.ScriptSortType.NUMBER).order(SortOrder.DESC))
                .addScriptField(Attributes.SIMILARITY, new Script(ScriptType.INLINE,"knn","binary_vector_score", params))
                .setQuery(
                        QueryBuilders.boolQuery()
                                .must(QueryBuilders.functionScoreQuery(
                                        QueryBuilders.matchAllQuery(),
                                        ScoreFunctionBuilders.scriptFunction(new Script(ScriptType.INLINE,"knn","binary_vector_score", params))).boostMode(CombineFunction.SUM).scoreMode(FiltersFunctionScoreQuery.ScoreMode.SUM)
                                ).must(
                                QueryBuilders.existsQuery(Attributes.ENC)
                        )
                ).get();
        Stream.of(response.getHits().getHits()).forEach(hit->{
            System.out.println("Hit "+hit.getId()+": "+new Gson().toJson(hit.getSource()));
            System.out.println("Fields "+hit.getId()+": "+new Gson().toJson(hit.getFields()));
            System.out.println("Score: "+hit.getScore());
        });


    }
}
