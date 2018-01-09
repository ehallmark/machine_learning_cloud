package elasticsearch;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
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
    public static final String NAME_FIELD = "name";
    public static final String PARENT_DIRS_FIELD = "parentDirs";
    public static final String USER_FIELD = "user";

    public static void index(String user, String id, List<String> data, String name, String... parentDirs) {
        Map<String,Object> dataMap = new HashMap<>(2);
        dataMap.put(DATA_FIELD,data);
        dataMap.put(USER_FIELD,user);
        if(name!=null) {
            dataMap.put(NAME_FIELD, name);
        }
        if(parentDirs!=null) {
            dataMap.put(PARENT_DIRS_FIELD, Arrays.asList(parentDirs));
        }
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

    public static String idFromName(String user, String name, String... parentDirs) {
        SearchResponse res = client.prepareSearch(INDEX).setTypes(TYPE).setFetchSource(new String[]{"_id"},new String[]{}).setSize(1).setFrom(0)
                .setQuery(QueryBuilders.boolQuery()
                        .filter(
                                QueryBuilders.boolQuery()
                                        .must(
                                                QueryBuilders.termQuery(NAME_FIELD,name)
                                        ).must(
                                        QueryBuilders.termQuery(USER_FIELD, user)
                                )
                        ).should(
                                QueryBuilders.termsQuery(PARENT_DIRS_FIELD, parentDirs)
                        )
                ).addSort(SortBuilders.scoreSort().order(SortOrder.DESC)).get();

        if(res!=null) {
            SearchHits searchHits = res.getHits();
            if(searchHits!=null) {
                SearchHit[] hits = searchHits.getHits();
                if(hits!=null&&hits.length>0) {
                    return  hits[0].getId().split("_")[0];
                }
            }
        }
        return null;
    }
}
