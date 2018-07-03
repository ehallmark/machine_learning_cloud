package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import user_interface.server.BigQueryServer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.highcharts.ColumnChart;
import user_interface.ui_models.charts.highcharts.HeatMapChart;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static j2html.TagCreator.*;

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
        String humanSearchType = collectAttr;
        String title = humanAttr + " "+chartTitle;


        List<String> yCategories = getCategoriesForAttribute(attribute);
        List<String> xCategories = null;
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        String subtitle = "";
        AbstractAttribute groupByAttr = findAttribute(groupByAttributes, groupedByAttrName);
        final boolean isGrouped = groupedByAttrName!=null;
        if(isGrouped) {
            subtitle = "Grouped by "+SimilarPatentServer.humanAttributeFor(groupedByAttrName);
            if(groupByAttr!=null) {
                xCategories = getCategoriesForAttribute(groupByAttr);
            }
        }
        if(xCategories == null) {
            throw new RuntimeException("Unable to find categories for grouped attribute: "+groupedByAttrName);
        }

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
        return Collections.singletonList(new HeatMapChart(parentOptions, title, subtitle, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, 0, collectorType, xCategories, yCategories));
    }


    @Override
    public String getType() {
        return "heatmap";
    }

}
