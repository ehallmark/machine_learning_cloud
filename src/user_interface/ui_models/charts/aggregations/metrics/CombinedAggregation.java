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
        if(collectByAttr==null) {
            // default to count
            if(mode!=null&&!mode.equals(Type.Count)) {
                throw new RuntimeException("Please choose a collect by attribute or collect by count.");
            }
            aggregation = base.getAggregation();
        } else {
            final boolean isNested = collectByAttr!=null&&collectByAttr.getParent()!=null&&!(collectByAttr instanceof AbstractChartAttribute)&&!collectByAttr.getParent().isObject();
            switch (mode) {
                case Max: {
                    aggregation = AggregationBuilders.max(name)
                            .field(collectByAttr.getFullName());
                    break;
                }
                case Min: {
                    aggregation = AggregationBuilders.min(name)
                            .field(collectByAttr.getFullName());
                    break;
                }
                case Sum: {
                    aggregation = AggregationBuilders.sum(name)
                            .field(collectByAttr.getFullName());
                    break;
                }
                case Average: {
                    aggregation = AggregationBuilders.avg(name)
                            .field(collectByAttr.getFullName());
                    break;
                }
                case Count: {
                    aggregation = AggregationBuilders.count(name)
                        .field(collectByAttr.getFullName());
                    break;
                }
                case Cardinality: {
                    aggregation = AggregationBuilders.cardinality(name)
                            .field(collectByAttr.getFullName());
                }
            }
            if(isNested) {
                aggregation = AggregationBuilders.nested(name+ AggregationChart.NESTED_SUFFIX, collectByAttr.getParent().getName())
                        .subAggregation(aggregation);
            }
            aggregation = base.getAggregation().subAggregation(
                    aggregation
            );
        }
    }

}
