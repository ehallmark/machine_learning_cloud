package user_interface.ui_models.charts.aggregations.buckets;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;

public class HistogramAggregation extends BucketAggregation {
    @Getter
    protected AggregationBuilder aggregation;
    public HistogramAggregation(String name, String field, Script script, double interval, Double xMin, Double xMax, Object missingVal) {
        HistogramAggregationBuilder _builder = AggregationBuilders.histogram(name);
        if(field!=null) _builder=_builder.field(field);
        if(script!=null) _builder=_builder.script(script);
        if(missingVal!=null) _builder = _builder.missing(missingVal);
        if(xMax!=null&&xMin!=null) _builder = _builder.extendedBounds(xMin,xMax);
        aggregation=_builder.interval(interval);
    }

}
