package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.buckets.DateHistogramAggregation;
import user_interface.ui_models.charts.highcharts.LineChart;

import java.time.LocalDate;
import java.util.*;

public class AggregateLineChart extends AggregationChart<LineChart> {
    private static final String AGG_SUFFIX = "_line";
    protected Map<String,LocalDate> attrToMinMap;
    protected Map<String,LocalDate> attrToMaxMap;
    public AggregateLineChart(Collection<AbstractAttribute> attributes, String name) {
        super(false,AGG_SUFFIX, attributes, Collections.emptyList(), name, false);
        this.attrToMaxMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToMinMap = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public AggregateLineChart dup() {
        return new AggregateLineChart(attributes,name);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(this.attrNames!=null) {
            this.attrNames.forEach(attr -> {
                Object min = SimilarPatentServer.extractString(params, attr.replace(".","")+SimilarPatentServer.LINE_CHART_MIN, null);
                Object max = SimilarPatentServer.extractString(params, attr.replace(".","")+SimilarPatentServer.LINE_CHART_MAX, null);
                if(min != null && min.toString().length()>0) {
                    try {
                        min = LocalDate.parse(min.toString());
                    } catch(Exception e) {
                        throw new RuntimeException("Error parsing date: "+min);
                    }
                    System.out.println("Found start date: "+min);
                } else {
                    min = null;
                }
                if(max != null && max.toString().length()>0) {
                    try {
                        max = LocalDate.parse(max.toString());
                    } catch(Exception e) {
                        throw new RuntimeException("Error parsing date: "+max);
                    }
                    System.out.println("Found end date: "+max);
                } else {
                    max = null;
                }
                if(min!=null) attrToMinMap.put(attr,(LocalDate) min);
                if(max!=null) attrToMaxMap.put(attr,(LocalDate) max);
            });
        }
    }

    @Override
    public List<? extends LineChart> create(AbstractAttribute attribute, Aggregations aggregations) {
        String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attribute.getFullName());
        String humanSearchType = combineTypesToString(searchTypes);
        String title = humanAttr + " Timeline";
        String xAxisSuffix = "";
        String yAxisSuffix = "";

        String seriesName = singularize(humanSearchType) + " Count";
        String attrName = attribute.getFullName();
        Aggregation agg = aggregations.get(attrName + aggSuffix);
        List<Map<String,Object>> bucketData = (List<Map<String,Object>>) agg.getMetaData().get("buckets");
        PointSeries series = new PointSeries();
        series.setName(seriesName);

        List<Series<?>> seriesList = new ArrayList<>();
        series.setName(title);
        series.setShowInLegend(false);
        for(int i = 0; i < bucketData.size(); i++) {
            Map<String,Object> bucket = bucketData.get(i);
            Point point = new Point(((String)bucket.get("key_as_string")), (Number)bucket.get("doc_count"));
            series.addPoint(point);
        }
        seriesList.add(series);
        return Collections.singletonList(new LineChart(false,title, "", seriesList, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, 0));
    }

    @Override
    public List<AbstractAggregation> getAggregations(AbstractAttribute attribute) {
        String attrName = attribute.getFullName();
        LocalDate xMin = attrToMinMap.get(attribute);
        LocalDate xMax = attrToMaxMap.get(attribute);
        if (attribute instanceof AbstractScriptAttribute) {
            return Collections.singletonList(
                    new DateHistogramAggregation(attrName + aggSuffix, null, ((AbstractScriptAttribute) attribute).getSortScript(), xMin,xMax, null)
            );
        } else {
            return Collections.singletonList(
                    new DateHistogramAggregation(attrName + aggSuffix, attrName, null, xMin,xMax,null)
            );
        }
    }

}
