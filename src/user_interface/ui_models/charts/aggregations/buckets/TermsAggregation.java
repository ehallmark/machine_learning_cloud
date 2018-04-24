package user_interface.ui_models.charts.aggregations.buckets;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

public class TermsAggregation extends BucketAggregation {
    @Getter
    protected AggregationBuilder aggregation;
    public TermsAggregation(String name, String field, Script script, Object missingVal, Integer size) {
        TermsAggregationBuilder _builder = AggregationBuilders.terms(name);
        if(field!=null) _builder=_builder.field(field);
        if(script!=null) _builder=_builder.script(script);
        if(missingVal!=null)_builder= _builder.missing(missingVal);
        if(size!=null)_builder=_builder.size(size);
        aggregation=_builder;
    }

}
