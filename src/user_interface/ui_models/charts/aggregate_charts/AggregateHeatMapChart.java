package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import user_interface.server.BigQueryServer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.highcharts.ArraySeries;
import user_interface.ui_models.charts.highcharts.HeatMapChart;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AggregateHeatMapChart extends AggregationChart<HeatMapChart> {
    private static final String AGG_SUFFIX = "_heatmap";
    public AggregateHeatMapChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Collection<AbstractAttribute> collectAttrs) {
        super(false,"Heat Map",AGG_SUFFIX, attributes, groupByAttrs, collectAttrs, Constants.HEAT_MAP);
    }

    @Override
    public AggregateHeatMapChart dup() {
        return new AggregateHeatMapChart(attributes,groupByAttributes,collectByAttributes);
    }

    @Override
    public List<? extends HeatMapChart> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        String humanAttr = SimilarPatentServer.humanAttributeFor(attrName);
        String collectAttr = attrToCollectByAttrMap.get(attrName);
        if(collectAttr==null) {
            collectAttr = "Assets";
        } else {
            collectAttr = BigQueryServer.fullHumanAttributeFor(collectAttr);
        }
        AbstractAttribute collectByAttr = findAttribute(collectByAttributes, collectAttr);
        int valueDecimals = 0;
        String valueSuffix = "";
        if(collectByAttr != null && collectByAttr instanceof RangeAttribute) {
            valueSuffix = ((RangeAttribute) collectByAttr).valueSuffix();
        }
        if(collectByAttr != null && collectByAttr.getType().equals("double")) {
            valueDecimals = 1;
        }
        String title = humanAttr + " "+chartTitle;
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        String yLabel = BigQueryServer.fullHumanAttributeFor(groupedByAttrName);
        String subtitle;
        AbstractAttribute groupByAttr = findAttribute(groupByAttributes, groupedByAttrName);
        subtitle = "Grouped by "+SimilarPatentServer.humanAttributeFor(groupedByAttrName);
        String xAxisSuffix;
        String yAxisSuffix;

        if(attribute instanceof RangeAttribute) {
            xAxisSuffix = ((RangeAttribute) attribute).valueSuffix();
        } else {
            xAxisSuffix = "";
        }
        if(groupByAttr instanceof RangeAttribute) {
            yAxisSuffix = ((RangeAttribute) groupByAttr).valueSuffix();
        } else {
            yAxisSuffix = "";
        }

        Type collectorType = attrToCollectTypeMap.getOrDefault(attrName, Type.Count);
        Options parentOptions = new Options();
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        parentOptions = createDataForAggregationChart(parentOptions,aggregations,attribute,attrName,title,null,false,includeBlank);
        List<String> xCategories = parentOptions.getSeries().isEmpty()? Collections.emptyList() :
                parentOptions.getSeries().stream().flatMap(series->((ArraySeries)series).getData().stream().map(data->(String)data.get(0))).distinct().sorted().collect(Collectors.toList());
        List<String> yCategories = parentOptions.getSeries().stream().map(series->series.getName()).collect(Collectors.toList());
        return Collections.singletonList(new HeatMapChart(parentOptions, title, subtitle, humanAttr, yLabel, collectorType, xCategories, yCategories));
    }


    @Override
    public String getType() {
        return "heatmap";
    }

}
