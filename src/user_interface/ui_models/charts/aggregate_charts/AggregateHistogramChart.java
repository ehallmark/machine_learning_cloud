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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static j2html.TagCreator.*;

public class AggregateHistogramChart extends AggregationChart<ColumnChart> {
    private static final String AGG_SUFFIX = "_hist";
    public AggregateHistogramChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Collection<AbstractAttribute> collectAttrs) {
        super(false,"Histogram",AGG_SUFFIX, attributes, groupByAttrs, collectAttrs, Constants.HISTOGRAM);
    }

    @Override
    public AggregateHistogramChart dup() {
        return new AggregateHistogramChart(attributes,groupByAttributes,collectByAttributes);
    }

    @Override
    public List<? extends ColumnChart> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        RangeAttribute rangeAttribute = (RangeAttribute)attribute;
        String humanAttr = SimilarPatentServer.humanAttributeFor(attrName);
        String collectAttr = attrToCollectByAttrMap.get(attrName);
        if(collectAttr==null) {
            collectAttr = "Assets";
        } else {
            collectAttr = BigQueryServer.fullHumanAttributeFor(collectAttr);
        }
        String humanSearchType = collectAttr;
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
        Type collectorType = attrToCollectTypeMap.getOrDefault(attrName, Type.Count);
        Options parentOptions = new Options();
        boolean drilldown = attrToDrilldownMap.getOrDefault(attrName, false);
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        parentOptions = createDataForAggregationChart(parentOptions,aggregations,attribute,attrName,title,null,drilldown,includeBlank);
        List<? extends Series> data = parentOptions.getSeries();
        if(!drilldown) {
            data.forEach(series -> {
                series.setShowInLegend(isGrouped);
            });
        }
        return Collections.singletonList(new ColumnChart(parentOptions, title, 0d, null, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, subtitle, 0, categories, collectorType));
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        Function<String,ContainerTag> additionalTagFunction = this::getAdditionalTagPerAttr;
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Arrays.asList(getSwapAxesAttrFieldName(attrName),getDrilldownAttrFieldName(attrName));
        Function2<ContainerTag,ContainerTag,ContainerTag> combineFunction = (tag1, tag2) -> div().withClass("row").with(
                div().withClass("col-9").with(
                        tag1
                ),div().withClass("col-3").with(
                        tag2
                )
        );
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,combineFunction,true);
    }


    private ContainerTag getAdditionalTagPerAttr(String attrName) {
        return div().withClass("row").with(
                div().withClass("col-6").with(
                        label("Drilldown").attr("title","Plot groups using drilldowns.").with(
                                br(),
                                input().withId(getDrilldownAttrFieldName(attrName)).withValue("off").withName(getDrilldownAttrFieldName(attrName)).withType("checkbox")
                        )
                ),div().withClass("col-6").with(
                        label("Swap Axes").attr("title","Swap axes of histogram drilldown chart.").with(
                                br(),
                                input().withId(getSwapAxesAttrFieldName(attrName)).withValue("off").withName(getSwapAxesAttrFieldName(attrName)).withType("checkbox")
                        )
                )
        );
    }


    @Override
    public String getType() {
        return "histogram";
    }

}
