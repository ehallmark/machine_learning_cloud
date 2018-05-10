package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.charts.highcharts.ColumnChart;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AggregateHistogramChart extends AggregationChart<ColumnChart> {
    private static final String AGG_SUFFIX = "_hist";
    public AggregateHistogramChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs) {
        super(false,"Histogram",AGG_SUFFIX, attributes, groupByAttrs, Constants.HISTOGRAM, false);
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
        String title = humanAttr + " "+chartTitle;

        String xAxisSuffix = rangeAttribute.valueSuffix();
        String yAxisSuffix = "";

        List<String> categories = getCategoriesForAttribute(attribute);

        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        String subtitle = "";
        final boolean isGrouped = groupedByAttrName!=null;
        if(isGrouped) {
            subtitle = "Grouped by "+SimilarPatentServer.humanAttributeFor(groupedByAttrName);
        }
        Options parentOptions = new Options();
        boolean drilldown = attrToDrilldownMap.getOrDefault(attrName, false);
        List<Series<?>> data = createDataForAggregationChart(parentOptions,aggregations,attribute,attrName,title,null,drilldown);
        data.forEach(series-> {
            series.setShowInLegend(false);
        });

        return Collections.singletonList(new ColumnChart(parentOptions, title, data, 0d, null, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, subtitle, 0, categories));
    }


    @Override
    public String getType() {
        return "histogram";
    }

}
