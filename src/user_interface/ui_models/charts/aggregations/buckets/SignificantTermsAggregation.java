package user_interface.ui_models.charts.aggregations.buckets;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.significant.SignificantTermsAggregationBuilder;

public class SignificantTermsAggregation extends BucketAggregation {
    public static final String SAMPLER_SUFFIX = "_samp";
    @Getter
    protected AggregationBuilder aggregation;
    public SignificantTermsAggregation(String name, String field, Script script, Object missingVal, Integer size) {
        SignificantTermsAggregationBuilder _builder = AggregationBuilders.significantTerms(name);
        if(field!=null) _builder=_builder.field(field);
        if(script!=null) _builder=_builder.script(script);
        if(missingVal!=null)_builder= _builder.missing(missingVal);
        if(size!=null)_builder=_builder.size(size);
        aggregation=_builder;
        aggregation=AggregationBuilders.sampler(name+SAMPLER_SUFFIX)
                .subAggregation(aggregation);
    }

}
