package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import j2html.tags.Tag;
import lombok.Getter;
import models.value_models.ValueMapNormalizer;
import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.ColumnChart;
import user_interface.ui_models.charts.highcharts.LineChart;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/18/2017.
 */
public class AbstractLineChart extends ChartAttribute {
    protected Collection<String> searchTypes;
    protected Integer max;
    protected Integer min;

    public AbstractLineChart() {
        super(Arrays.asList(Constants.FILING_DATE, Constants.RECORDED_DATE, Constants.PUBLICATION_DATE, Constants.EXPIRATION_DATE, Constants.PRIORITY_DATE));
    }

    @Override
    public ChartAttribute dup() {
        return new AbstractLineChart();
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                div().withClass("row").with(
                        div().withClass("col-6").with(
                                label("Min"),br(),input().withId(SimilarPatentServer.LINE_CHART_MIN).withName(SimilarPatentServer.LINE_CHART_MIN).withType("number").withClass("form-control")
                        ), div().withClass("col-6").with(
                                label("Max"),br(),input().withId(SimilarPatentServer.LINE_CHART_MAX).withName(SimilarPatentServer.LINE_CHART_MAX).withType("number").withClass("form-control")
                        )
                ),
                SimilarPatentServer.technologySelect(Constants.LINE_CHART,getAttributes())
        );
    }

    @Override
    public String getType() {
        return "line";
    }


    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attributes = SimilarPatentServer.extractArray(params, Constants.LINE_CHART);
        searchTypes = SimilarPatentServer.extractArray(params,  Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }

        min = SimilarPatentServer.extractInt(params, SimilarPatentServer.LINE_CHART_MIN, null);
        max = SimilarPatentServer.extractInt(params, SimilarPatentServer.LINE_CHART_MAX, null);
    }
    
    @Override
    public List<? extends AbstractChart> create(PortfolioList portfolioList, int i) {
        return Stream.of(attributes.get(i)).map(attribute->{
            String humanAttr = SimilarPatentServer.humanAttributeFor(attribute);
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanAttr + " Timeline";
            String xAxisSuffix = "";
            String yAxisSuffix = "";
            return new LineChart(title, collectTimelineData(Arrays.asList(portfolioList.getItemList()), attribute),xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType,  0, min, max);
        }).collect(Collectors.toList());
    }

    protected static String combineTypesToString(Collection<String> types) {
        if(types.isEmpty()) return "";
        types = types.stream().map(type-> SimilarPatentServer.humanAttributeFor(type)).collect(Collectors.toList());
        return String.join(" and ", types);
    }

    private List<Series<?>> collectTimelineData(Collection<Item> data, String attribute) {
        PointSeries series = new PointSeries();
        Map<Integer,Long> dataMap = data.stream().filter(item->{Object r = item.getData(attribute); return r!=null && r.toString().length()>4;}).collect(Collectors.groupingBy(item->Integer.valueOf(item.getData(attribute).toString().substring(0,4)),Collectors.counting()));
        dataMap.entrySet().stream().sorted(Comparator.comparing(e->e.getKey())).forEach(e ->{
            series.addPoint(new Point(e.getKey(),e.getValue()));
        });
        return Arrays.asList(
            series
        );
    }
}
