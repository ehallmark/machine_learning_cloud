package user_interface.ui_models.charts.aggregations.metrics;

import lombok.Getter;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregate_charts.AggregationChart;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;

public class CombinedAggregation implements AbstractAggregation {
    @Getter
    protected AggregationBuilder aggregation;

    public CombinedAggregation(BucketAggregation base, String name, String nestedName, AbstractAttribute collectByAttr, Type mode, boolean baseIsNested) {
        if(collectByAttr==null) {
            // default to count
            if(mode!=null&&!mode.equals(Type.Count)) {
                throw new RuntimeException("Please choose a collect by attribute or collect by count.");
            }
            aggregation = base.getAggregation();
        } else {
            boolean isScript = collectByAttr instanceof AbstractScriptAttribute;
            final boolean isNested = collectByAttr.getParent()!=null&&!(collectByAttr.getParent() instanceof AbstractChartAttribute)&&!collectByAttr.getParent().isObject();
            switch (mode) {
                case Max: {
                    if(isScript) {
                        aggregation = AggregationBuilders.max(name)
                                .script(((AbstractScriptAttribute)collectByAttr).getSortScript());
                    } else {
                        aggregation = AggregationBuilders.max(name)
                                .field(collectByAttr.getFullName());
                    }
                    break;
                }
                case Min: {
                    if(isScript) {
                        aggregation = AggregationBuilders.min(name)
                                .script(((AbstractScriptAttribute)collectByAttr).getSortScript());
                    } else {
                        aggregation = AggregationBuilders.min(name)
                                .field(collectByAttr.getFullName());
                    }
                    break;
                }
                case Sum: {
                    if(isScript) {
                        aggregation = AggregationBuilders.sum(name)
                                .script(((AbstractScriptAttribute)collectByAttr).getSortScript());
                    } else {
                        aggregation = AggregationBuilders.sum(name)
                                .field(collectByAttr.getFullName());
                    }
                    break;
                }
                case Average: {
                    if(isScript) {
                        aggregation = AggregationBuilders.avg(name)
                                .script(((AbstractScriptAttribute)collectByAttr).getSortScript());
                    } else {
                        aggregation = AggregationBuilders.avg(name)
                                .field(collectByAttr.getFullName());
                    }
                    break;
                }
                case Count: {
                    if(isScript) {
                        aggregation = AggregationBuilders.count(name)
                                .script(((AbstractScriptAttribute)collectByAttr).getSortScript());
                    } else {
                        aggregation = AggregationBuilders.count(name)
                                .field(collectByAttr.getFullName());
                    }
                    break;
                }
                case Cardinality: {
                    if(isScript) {
                        aggregation = AggregationBuilders.cardinality(name)
                                .script(((AbstractScriptAttribute)collectByAttr).getSortScript());
                    } else {
                        aggregation = AggregationBuilders.cardinality(name)
                                .field(collectByAttr.getFullName());
                    }
                    break;
                } default: {
                    if(isScript) {
                        aggregation = AggregationBuilders.extendedStats(name)
                                .script(((AbstractScriptAttribute)collectByAttr).getSortScript());
                    } else {
                        aggregation = AggregationBuilders.extendedStats(name)
                                .field(collectByAttr.getFullName());
                    }
                    break;
                }
            }


            if(isNested) {
                aggregation = AggregationBuilders.nested(nestedName, collectByAttr.getParent().getName())
                        .subAggregation(aggregation);
            }

            if(baseIsNested) {
                // needs reverse nested clause
                System.out.println("Using reverse nested clause for agg: "+base.getAggregation().toString());
                aggregation = AggregationBuilders.reverseNested(aggregation.getName() + AggregationChart.REVERSE_NESTED_SUFFIX)
                        .subAggregation(aggregation);
            }

            aggregation = base.getAggregation().subAggregation(
                    aggregation
            );

        }
    }

}
