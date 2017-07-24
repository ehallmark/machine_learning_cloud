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
import user_interface.ui_models.filters.AbstractFilter;
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

    public static Item[] searchForAssets(int limit, Collection<String> attributes, Collection<? extends AbstractFilter> filters) {
        try {
            SearchRequestBuilder request = client.prepareSearch(INDEX_NAME)
                    .setTypes(TYPE_NAME)
                    .storedFields(attributes.toArray(new String[attributes.size()]))
                    //.setQuery(queryBuilder)
                    .setSize(limit)
                    .setFrom(0);
            BoolQueryBuilder filterBuilder = QueryBuilders.boolQuery();
            // other filters
            for(AbstractFilter filter : filters) {
                filterBuilder = filterBuilder
                        .must(filter.getFilterQuery());
            }
            request = request.setPostFilter(filterBuilder);
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
