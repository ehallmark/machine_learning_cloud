package elasticsearch;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.util.*;

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
        client.prepareIndex(INDEX,TYPE,id+"_"+user).setSource(dataMap).get();
    }

    public static void delete(String user, String id) {
        client.prepareDelete(INDEX,TYPE,id+"_"+user).get();
    }

    public static List<String> get(String label) {
        SearchResponse res = client.prepareSearch(INDEX).setTypes(TYPE).setFetchSource(new String[]{DATA_FIELD},new String[]{}).setSize(1).setFrom(0).setQuery(QueryBuilders.idsQuery(TYPE).addIds(label)).get();
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
    public static List<String> get(String user, String id) {
        return get(id+"_"+user);
    }

    public static void rename(String user, String id) {
        Map<String,Object> data = new HashMap<>();
        client.prepareUpdate(INDEX,TYPE,id+"_"+user)
                .setDoc(data).get();
    }


}
