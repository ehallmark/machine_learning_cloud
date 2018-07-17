package elasticsearch;

import lombok.Getter;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import user_interface.ui_models.portfolios.items.Item;

import java.util.List;

public class ElasticSearchResponse {
    @Getter
    private List<Item> items;
    @Getter
    private Aggregations aggregations;
    @Getter
    private long totalCount;
    @Getter
    private SearchRequestBuilder requestBuilder;
    @Getter
    private QueryBuilder query;
    public ElasticSearchResponse(SearchRequestBuilder requestBuilder, QueryBuilder query, List<Item> items, Aggregations aggregations, long totalCount) {
        this.requestBuilder = requestBuilder;
        this.query = query;
        this.items=items;
        this.aggregations=aggregations;
        this.totalCount=totalCount;
    }
}
