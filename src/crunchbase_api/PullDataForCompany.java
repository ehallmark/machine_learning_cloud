package crunchbase_api;

import com.google.gson.Gson;
import elasticsearch.DataSearcher;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.google.elasticsearch.Attributes;
import seeding.google.mongo.ingest.IngestPatents;
import user_interface.ui_models.portfolios.items.Item;

import java.util.HashMap;
import java.util.Map;

public class PullDataForCompany {
    private static Map<String, Object> getDataForCompany(String company) {
        int limit = 1;
        Map<String, Object> data = new HashMap<>();
        SearchResponse response = DataSearcher.getClient().prepareSearch(IngestPatents.INDEX_NAME)
                .setTypes(IngestPatents.TYPE_NAME)
                .setQuery(QueryBuilders.boolQuery()
                    .filter(QueryBuilders.matchPhraseQuery(Attributes.LATEST_ASSIGNEES+"."+Attributes.LATEST_ASSIGNEE, company))
                )
                .setTrackScores(false)
                .setSize(limit)
                .setFetchSource(true)
               // .addAggregation() // TODO
                .get();
        DataSearcher.iterateOverSearchResults(response, hit -> {
            Item item = new Item(hit.getId());
            item.getDataMap().putAll(hit.getSource());
            data.put(item.getName(), item.getDataMap());

            return item;
        }, limit, false, false);
        return data;
    }


    public static void main(String[] args) throws Exception {
        String[] companies = new String[]{
                "microsoft",
                "google llc",
                "ibm",
                "international business machines",
                "google",
                "lyft",
                "uber",
                "coinbase"
        };

        for(String company : companies) {
            System.out.println("Data for "+company+": "+new Gson().toJson(getDataForCompany(company)));
        }
    }
}
