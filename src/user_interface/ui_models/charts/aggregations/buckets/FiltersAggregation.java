package user_interface.ui_models.charts.aggregations.buckets;

import lombok.Getter;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.filters.FiltersAggregationBuilder;

public class FiltersAggregation extends BucketAggregation {
    @Getter
    protected AggregationBuilder aggregation;
    public FiltersAggregation(String name, boolean otherBucket, String otherBucketKey, QueryBuilder... builders) {
        FiltersAggregationBuilder _builder = AggregationBuilders.filters(name,builders)
                .otherBucket(otherBucket);
        if(otherBucketKey!=null&&otherBucket) {
            _builder=_builder.otherBucketKey(otherBucketKey);
        }
        aggregation=_builder;
    }

}
