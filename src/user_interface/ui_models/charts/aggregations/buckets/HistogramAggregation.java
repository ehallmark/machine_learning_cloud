package user_interface.ui_models.charts.aggregations.buckets;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.nd4j.linalg.primitives.Pair;

import java.util.List;

public class HistogramAggregation extends BucketAggregation {
    @Getter
    protected AggregationBuilder aggregation;
    public HistogramAggregation(String name, String field, Script script, List<Pair<Number,Number>> ranges, Object missingVal) {
        RangeAggregationBuilder _builder = AggregationBuilders.range(name);
        if(field!=null) _builder=_builder.field(field);
        if(script!=null) _builder=_builder.script(script);
        if(missingVal!=null) _builder = _builder.missing(missingVal);
        if(ranges!=null) {
            for(Pair<Number,Number> range : ranges) {
                Number mi = range.getFirst();
                Number ma = range.getSecond();
                if(mi==null) {
                    _builder=_builder.addUnboundedTo(ma.doubleValue());
                } else if(ma==null) {
                    _builder=_builder.addUnboundedFrom(mi.doubleValue());
                } else {
                    _builder=_builder.addRange(mi.doubleValue(),ma.doubleValue());
                }
            }
        }
        aggregation=_builder;
    }

}
