package elasticsearch;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by Evan on 12/23/2017.
 */
public class DatasetIndex {
    private static TransportClient client = MyClient.get();
    public static final String INDEX = "datasets";
    public static final String TYPE = "users";
    public static final String DATA_FIELD = "data";

    public static void index(String user, String id, List<String> data) {
        Map<String,Object> dataMap = new HashMap<>(2);
        dataMap.put(DATA_FIELD,data);
        client.prepareIndex(INDEX,TYPE,user+id).setSource(dataMap).get();
    }

    public static void delete(String user, String id) {
        client.prepareDelete(INDEX,TYPE,user+id).get();
    }

    public static List<String> get(String user, String id) {
        SearchResponse res = client.prepareSearch(INDEX).setTypes(TYPE).setFetchSource(new String[]{DATA_FIELD},new String[]{}).setSize(1).setFrom(0).setQuery(QueryBuilders.idsQuery(TYPE).addIds(user+id)).get();
        if(res!=null) {
            SearchHits searchHits = res.getHits();
            if(searchHits!=null) {
                SearchHit[] hits = searchHits.getHits();
                if(hits!=null&&hits.length>0) {
                    return (List<String>) hits[0].getSource().getOrDefault(DATA_FIELD, Collections.emptyList());
                }
            }
        }
        return Collections.emptyList();
    }
}
