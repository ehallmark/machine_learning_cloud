package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.charts.highcharts.ColumnChart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AggregateHistogramChart extends AggregationChart<ColumnChart> {
    private static final String AGG_SUFFIX = "_hist";
    public AggregateHistogramChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs) {
        super(false,AGG_SUFFIX, attributes, groupByAttrs, Constants.HISTOGRAM, false);
    }

    @Override
    public AggregateHistogramChart dup() {
        return new AggregateHistogramChart(attributes,groupByAttributes);
    }

    @Override
    public List<? extends ColumnChart> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        RangeAttribute rangeAttribute = (RangeAttribute)attribute;
        String humanAttr = SimilarPatentServer.humanAttributeFor(attribute.getFullName());
        String humanSearchType = combineTypesToString(searchTypes);
        String title = humanAttr + " Histogram";

        double min = rangeAttribute.min().doubleValue();
        double max = rangeAttribute.max().doubleValue();
        int nBins = rangeAttribute.nBins();
        String xAxisSuffix = rangeAttribute.valueSuffix();
        String yAxisSuffix = "";

        List<String> categories = new ArrayList<>();
        int step = (int) Math.round((max-min)/nBins);
        for(int j = 0; j < max; j += step) {
            categories.add(String.valueOf(j) + "-" + String.valueOf(j+step));
        }

        PointSeries series = new PointSeries();
        series.setName(title);
        List<Series<?>> seriesList = new ArrayList<>();
        series.setName(title);
        series.setShowInLegend(false);

        // sr is here your SearchResponse object
        Histogram agg = (Histogram) handlePotentiallyNestedAgg(aggregations,attrName);
        // For each entry
        for (Histogram.Bucket entry : agg.getBuckets()) {
            String keyAsString = entry.getKeyAsString(); // Key as String
            long docCount = entry.getDocCount();         // Doc count
            Point point = new Point(keyAsString,docCount);
            series.addPoint(point);
        }
        seriesList.add(series);
        return Collections.singletonList(new ColumnChart(title, seriesList, 0d, null, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, name, 0, categories));
    }


    @Override
    public String getType() {
        return "histogram";
    }

}
