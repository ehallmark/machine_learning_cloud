package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.buckets.HistogramAggregation;
import user_interface.ui_models.charts.highcharts.ColumnChart;

import java.util.*;

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
    public List<? extends ColumnChart> create(AbstractAttribute attribute, Aggregations aggregations) {
        String attrName = attribute.getFullName();
        Aggregation agg = aggregations.get(attrName + aggSuffix);

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

        List<Map<String,Object>> bucketData = (List<Map<String,Object>>) agg.getMetaData().get("buckets");
        PointSeries series = new PointSeries();
        series.setName(title);
        List<Series<?>> seriesList = new ArrayList<>();
        series.setName(title);
        series.setShowInLegend(false);
        for(int i = 0; i < bucketData.size(); i++) {
            Map<String,Object> bucket = bucketData.get(i);
            Point point = new Point(categories.get(i), (Number)bucket.get("doc_count"));
            series.addPoint(point);
        }
        seriesList.add(series);
        return Collections.singletonList(new ColumnChart(title, seriesList, 0d, null, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, name, 0, categories));
    }

    @Override
    public List<AbstractAggregation> getAggregations(AbstractAttribute attribute) {
        String attrName = attribute.getFullName();
        RangeAttribute rangeAttribute = (RangeAttribute)attribute;
        if (attribute instanceof AbstractScriptAttribute) {
            return Collections.singletonList(
                    new HistogramAggregation(attrName + aggSuffix, null, ((AbstractScriptAttribute) attribute).getSortScript(), (rangeAttribute.max().doubleValue()-rangeAttribute.min().doubleValue())/rangeAttribute.nBins(), rangeAttribute.min().doubleValue(), rangeAttribute.max().doubleValue(), rangeAttribute.missing())
            );
        } else {
            return Collections.singletonList(
                    new HistogramAggregation(attrName + aggSuffix, attrName, null, (rangeAttribute.max().doubleValue()-rangeAttribute.min().doubleValue())/rangeAttribute.nBins(), rangeAttribute.min().doubleValue(), rangeAttribute.max().doubleValue(), rangeAttribute.missing())
            );
        }
    }
}
