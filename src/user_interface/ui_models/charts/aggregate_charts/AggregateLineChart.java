package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.highcharts.LineChart;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public class AggregateLineChart extends AggregationChart<LineChart> {
    private static final String AGG_SUFFIX = "_line";
    protected Map<String,LocalDate> attrToMinMap;
    protected Map<String,LocalDate> attrToMaxMap;
    public AggregateLineChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs) {
        super(false,"Timeline",AGG_SUFFIX, attributes, groupByAttrs, Constants.LINE_CHART, false);
        this.attrToMaxMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToMinMap = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public AggregateLineChart dup() {
        return new AggregateLineChart(attributes,groupByAttributes);
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
    public List<? extends LineChart> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attribute.getFullName());
        String humanSearchType = combineTypesToString(searchTypes);
        String title = humanAttr + " "+chartTitle;
        String xAxisSuffix = "";
        String yAxisSuffix = "";

        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        String subtitle = "";
        final boolean isGrouped = groupedByAttrName!=null;
        if(isGrouped) {
            subtitle = "Grouped by "+SimilarPatentServer.humanAttributeFor(groupedByAttrName);
        }
        List<Series<?>> data = createDataForAggregationChart(aggregations,attribute,attrName,title,null);
        data.forEach(series->{
            series.setShowInLegend(false);
        });
        List<String> categories = data.isEmpty() ? Collections.singletonList("0")
                : data.get(0).getData().stream().map(p->((Point)p).getName()).collect(Collectors.toList());
        System.out.println("Categories for timeline: "+String.join(", ",categories));
        return Collections.singletonList(new LineChart(false,title, subtitle, data, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, 0,categories));
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        Function<String,ContainerTag> additionalTagFunction = this::getAdditionalTagPerAttr;
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Arrays.asList(idFromName(attrName)+SimilarPatentServer.LINE_CHART_MIN,idFromName(attrName)+SimilarPatentServer.LINE_CHART_MAX);
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,(tag1,tag2)->div().with(tag1,tag2),true);
    }


    private ContainerTag getAdditionalTagPerAttr(String attrName) {
        attrName = idFromName(attrName);
        return div().withClass("row").with(
                div().withClass("col-6").with(
                        label("Min Date").attr("style","width: 100%;").with(
                                br(),
                                input().withId(attrName+SimilarPatentServer.LINE_CHART_MIN).attr("style","height: 28px;").withName(attrName+SimilarPatentServer.LINE_CHART_MIN).withType("text").withClass("datepicker form-control")
                        )
                ), div().withClass("col-6").with(
                        label("Max Date").attr("style","width: 100%;").with(
                                br(),input().withId(attrName+SimilarPatentServer.LINE_CHART_MAX).attr("style","height: 28px;").withName(attrName+SimilarPatentServer.LINE_CHART_MAX).withType("text").withClass("datepicker form-control")
                        )
                )
        );
    }


    @Override
    public String getType() {
        return "line";
    }

    public LocalDate getMin(String attrName) {
        return attrToMinMap.get(attrName);
    }

    public LocalDate getMax(String attrName) {
        return attrToMaxMap.get(attrName);
    }


}
