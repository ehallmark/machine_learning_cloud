package elasticsearch;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Evan on 7/22/2017.
 */
public class DataSearcher {
    private static TransportClient client = MyClient.get();
    private static final String INDEX_NAME = DataIngester.INDEX_NAME;
    private static final String TYPE_NAME = DataIngester.TYPE_NAME;

    public static Item[] searchForAssets(String[] ids, PortfolioList.Type type, String advancedKeywords, String keywordsToInclude, String keywordsToExclude, int limit, Collection<String> attributes) {
        try {
            // includes
            BoolQueryBuilder query = QueryBuilders.boolQuery();
            if(ids!=null) {
                query.must(QueryBuilders.idsQuery(TYPE_NAME).addIds(ids));
            }
            String[] includePhrases = keywordsToInclude.split("\\n");
            for(String phrase: includePhrases) {
                query = query.must(QueryBuilders.matchPhraseQuery("tokens",phrase.trim()));
            }
            // excludes
            String[] excludePhrases = keywordsToExclude.split("\\n");
            for(String phrase: excludePhrases) {
                query = query.mustNot(QueryBuilders.matchPhraseQuery("tokens",phrase.trim()));
            }
            // advanced
            query = query.must(QueryBuilders.simpleQueryStringQuery(advancedKeywords)
                    .defaultOperator(Operator.AND)
                    .analyzeWildcard(false)
                    .field("tokens"));

            SearchRequestBuilder request = client.prepareSearch(INDEX_NAME)
                    .setTypes(TYPE_NAME)
                    .storedFields(attributes.toArray(new String[attributes.size()]))
                    .setQuery(query)
                    .setSize(limit)
                    .setFrom(0);
            // check for full asset search
            QueryBuilder filter;
            if(type.equals(PortfolioList.Type.assets)) {
                filter = QueryBuilders.boolQuery()
                        .should(QueryBuilders.termQuery("doc_type", PortfolioList.Type.applications.toString()))
                        .should(QueryBuilders.termQuery("doc_type", PortfolioList.Type.patents.toString()));
            } else {
                filter = QueryBuilders.termQuery("doc_type", type.toString());
            }
            request = request.setPostFilter(filter);
            SearchResponse response = request.get();
            return Arrays.stream(response.getHits().getHits()).map(hit->hitToItem(hit)).toArray(size->new Item[size]);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error during keyword search: "+e.getMessage());
        }
    }


    private static Item hitToItem(SearchHit hit) {
        Item item = new Item(hit.getId());
        hit.getFields().forEach((k,v)->{
            item.addData(k,v.getValue());
        });
        return item;
    }
}
