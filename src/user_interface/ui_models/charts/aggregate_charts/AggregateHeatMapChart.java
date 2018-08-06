package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import spark.Request;
import user_interface.server.BigQueryServer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.highcharts.ArraySeries;
import user_interface.ui_models.charts.highcharts.HeatMapChart;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        Function<String,ContainerTag> additionalTagFunction = this::getAdditionalTagPerAttr;
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Arrays.asList(getIncludeRemainingField(attrName),getMaxSlicesField(attrName));
        Function2<ContainerTag,ContainerTag,ContainerTag> combineFunction = (tag1, tag2) -> div().withClass("row").with(
                div().withClass("col-9").with(
                        tag1
                ),div().withClass("col-3").with(
                        tag2
                )
        );
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,combineFunction,true,loadChildren,idToTagMap);
    }



    private ContainerTag getAdditionalTagPerAttr(String attrName) {
        return div().withClass("row").with(
                div().withClass("col-6").with(
                        label("Max Rows").attr("title", "The maximum number of rows for this heat map chart.").attr("style","width: 100%;").with(
                                br(),
                                input().withId(getMaxSlicesField(attrName)).withName(getMaxSlicesField(attrName)).withType("number").withClass("form-control").withValue("20")
                        )
                ), div().withClass("col-6").with(
                        label("Include Remaining").attr("title", "Including remaining counts in the heat map chart.").with(
                                br(),
                                input().withId(getIncludeRemainingField(attrName)).withName(getIncludeRemainingField(attrName)).withType("checkbox").withValue("off")
                        )
                )
        );
    }


    @Override
    public List<? extends HeatMapChart> create(Request req,  String attrName, AbstractAttribute attribute, AbstractAttribute groupByAttribute, AbstractAttribute collectByAttribute, Aggregations aggregations) {
        String humanAttr = SimilarPatentServer.humanAttributeFor(attrName);
        String title = humanAttr + " "+chartTitle;
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        String yLabel = BigQueryServer.fullHumanAttributeFor(groupedByAttrName);
        String subtitle;
        subtitle = "Grouped by "+SimilarPatentServer.humanAttributeFor(groupedByAttrName);
        Type collectorType = attrToCollectTypeMap.getOrDefault(attrName, Type.Count);
        Options parentOptions = new Options();
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        Integer limit = attrToLimitMap.getOrDefault(attrName, DEFAULT_MAX_SLICES);
        boolean includedRemaining = attrToIncludeRemainingMap.getOrDefault(attrName, false);
        if(!includedRemaining) {
            limit = null; // turns off accumulating remaining pie piece
        }
        parentOptions = createDataForAggregationChart(parentOptions,aggregations,attrName,attribute,groupByAttribute,collectByAttribute,title,limit,false,includeBlank);
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
