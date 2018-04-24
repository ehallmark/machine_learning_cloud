package user_interface.ui_models.charts.aggregations.buckets;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.ExtendedBounds;

import java.time.LocalDate;
import java.util.TimeZone;

public class DateHistogramAggregation extends BucketAggregation {
    @Getter
    protected AggregationBuilder aggregation;
    public DateHistogramAggregation(String name, String field, Script script, LocalDate xMin, LocalDate xMax, Object missingVal) {
        DateHistogramAggregationBuilder _builder = AggregationBuilders.dateHistogram(name);
        if(field!=null) _builder=_builder.field(field);
        if(script!=null) _builder=_builder.script(script);
        if(missingVal!=null) _builder = _builder.missing(missingVal);
        if(xMax!=null&&xMin!=null) _builder = _builder.extendedBounds(new ExtendedBounds(dateToLong(xMin),dateToLong(xMax)));
        aggregation = _builder.dateHistogramInterval(DateHistogramInterval.YEAR).format("yyyy");
    }

    private static long dateToLong(LocalDate date) {
        return date.atStartOfDay().atZone(TimeZone.getTimeZone("UTC").toZoneId()).toInstant().toEpochMilli();
    }
}
