package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import j2html.tags.Tag;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.LineChart;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.function.Function;
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
        super(Arrays.asList(Constants.FILING_DATE, Constants.ASSIGNMENTS+"."+Constants.RECORDED_DATE, Constants.ASSIGNMENTS+"."+Constants.EXECUTION_DATE, Constants.PUBLICATION_DATE, Constants.EXPIRATION_DATE, Constants.PRIORITY_DATE),Constants.LINE_CHART);
    }

    @Override
    public ChartAttribute dup() {
        return new AbstractLineChart();
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
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
            return new LineChart(title, collectTimelineData(portfolioList.getItemList(), attribute, humanSearchType),xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType,  0, min, max);
        }).collect(Collectors.toList());
    }

    private List<Series<?>> collectTimelineData(Collection<Item> data, String attribute, String humanSearchType) {
        PointSeries series = new PointSeries();
        series.setName(singularize(humanSearchType)+ " Count");
        Map<Integer,Long> dataMap = (Map<Integer,Long>) data.stream().flatMap(item-> {
            Object r = item.getData(attribute);
            if (r != null) {
                if (r instanceof Collection) {
                    return ((Collection) r).stream();
                } else {
                    return Stream.of(r);
                }
            }
            return Stream.empty();
        }).map(item->{
            System.out.println("timeline date attr: "+item);
            if(item!=null&&item.toString().length()>4) {
                return Integer.valueOf(item.toString().substring(0, 4));
            } else return null;
        }).filter(year->year!=null).collect(Collectors.groupingBy(Function.identity(),Collectors.counting()));

        dataMap.entrySet().stream().sorted(Comparator.comparing(e->e.getKey())).forEach(e ->{
            series.addPoint(new Point(e.getKey(),e.getValue()));
        });
        return Arrays.asList(
            series
        );
    }

    private static String singularize(String in) {
        return in.endsWith("s") && in.length()>1 ? in.substring(0,in.length()-1) : in;
    }
}
