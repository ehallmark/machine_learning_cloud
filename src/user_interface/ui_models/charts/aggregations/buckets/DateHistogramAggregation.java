package user_interface.ui_models.charts.aggregations.buckets;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.date.DateRangeAggregationBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class DateHistogramAggregation extends BucketAggregation {
    @Getter
    protected AggregationBuilder aggregation;
    public DateHistogramAggregation(String name, String field, Script script, LocalDate xMin, LocalDate xMax, Object missingVal) {
        DateRangeAggregationBuilder _builder = AggregationBuilders.dateRange(name).format("yyyy");
        if(field!=null) _builder=_builder.field(field);
        if(script!=null) _builder=_builder.script(script);
        if(missingVal!=null) _builder = _builder.missing(missingVal);
        LocalDate date = xMin;
        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_DATE;
        while(date.isBefore(xMax)) {
            _builder.addRange(date.format(dateFormatter),date.plusYears(1).format(dateFormatter));
            date = date.plusYears(1);
        }
        aggregation = _builder;
    }

    private static long dateToLong(LocalDate date) {
        return date.atStartOfDay().atZone(TimeZone.getTimeZone("UTC").toZoneId()).toInstant().toEpochMilli();
    }
}
