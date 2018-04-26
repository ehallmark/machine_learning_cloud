package user_interface.ui_models.charts.aggregations.metrics;

import lombok.Getter;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregate_charts.AggregationChart;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;

public class CombinedAggregation implements AbstractAggregation {
    @Getter
    protected AggregationBuilder aggregation;

    public CombinedAggregation(BucketAggregation base, String name, AbstractAttribute collectByAttr, Type mode) {
        if(mode==null) {
            // default to count
            aggregation = base.getAggregation();
        } else {
            final boolean isNested = collectByAttr!=null&&collectByAttr.getParent()!=null&&!(collectByAttr instanceof AbstractChartAttribute)&&!collectByAttr.getParent().isObject();
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
                case Count: {
                    aggregation = base.getAggregation();
                    break;
                }
            }
            if(isNested) {
                aggregation = AggregationBuilders.nested(name+ AggregationChart.NESTED_SUFFIX, collectByAttr.getParent().getName())
                        .subAggregation(aggregation);
            }
            if(!mode.equals(Type.Count)) {
                aggregation = base.getAggregation().subAggregation(
                        aggregation
                );
            }
        }
    }

}
