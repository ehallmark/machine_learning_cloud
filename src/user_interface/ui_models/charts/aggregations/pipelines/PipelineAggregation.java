package user_interface.ui_models.charts.aggregations.pipelines;

import lombok.Getter;
import org.elasticsearch.search.aggregations.PipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.Type;

public class PipelineAggregation implements AbstractAggregation {
    @Getter
    protected PipelineAggregationBuilder aggregation;

    public PipelineAggregation(String name, String bucketPath, Type mode) {
        switch (mode) {
            case All: {
                aggregation = PipelineAggregatorBuilders.statsBucket(name,bucketPath);
                break;
            }
            case Max: {
                aggregation = PipelineAggregatorBuilders.maxBucket(name,bucketPath);
                break;
            }
            case Min: {
                aggregation = PipelineAggregatorBuilders.minBucket(name,bucketPath);
                break;
            }
            case Sum: {
                aggregation = PipelineAggregatorBuilders.sumBucket(name,bucketPath);
                break;
            }
            case Average: {
                aggregation = PipelineAggregatorBuilders.avgBucket(name,bucketPath);
                break;
            }
        }
    }

}
