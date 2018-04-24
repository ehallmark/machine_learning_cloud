package elasticsearch;

import lombok.Getter;
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
    public ElasticSearchResponse(List<Item> items, Aggregations aggregations, long totalCount) {
        this.items=items;
        this.aggregations=aggregations;
        this.totalCount=totalCount;
    }
}
