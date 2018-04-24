package user_interface.ui_models.charts.aggregations;

import org.elasticsearch.search.aggregations.AggregationBuilder;

public interface AbstractAggregation {

    AggregationBuilder getAggregation();
}
