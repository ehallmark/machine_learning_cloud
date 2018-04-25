package user_interface.ui_models.charts.aggregations.metrics;

import lombok.Getter;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;

public class CombinedAggregation implements AbstractAggregation {
    @Getter
    protected AggregationBuilder aggregation;

    public CombinedAggregation(BucketAggregation base, String name, Type mode) {
        if(mode==null) {
            // default to count
            aggregation = base.getAggregation();
        } else {
            switch (mode) {
                case Max: {
                    aggregation = AggregationBuilders.max(name);
                    break;
                }
                case Min: {
                    aggregation = AggregationBuilders.min(name);
                    break;
                }
                case Sum: {
                    aggregation = AggregationBuilders.sum(name);
                    break;
                }
                case Average: {
                    aggregation = AggregationBuilders.avg(name);
                    break;
                }
            }
            aggregation = base.getAggregation().subAggregation(
                    aggregation
            );
        }
    }

}
