package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import elasticsearch.DataSearcher;
import j2html.tags.Tag;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
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
    protected Map<String,Number> attrToMinMap;
    protected Map<String,Number> attrToMaxMap;
    public AbstractLineChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttributes) {
        super(attributes,groupedByAttributes,Constants.LINE_CHART);
        this.attrToMaxMap = Collections.synchronizedMap(new HashMap<>());
        this.attrToMinMap = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public ChartAttribute dup() {
        return new AbstractLineChart(attributes,groupByAttributes);
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        Function<String,Tag> additionalTagFunction = this::getAdditionalTagPerAttr;
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,true);
    }

    private Tag getAdditionalTagPerAttr(String attrName) {
        attrName = attrName.replace(getName().replace("[","").replace("]","")+".","");
        return div().withClass("row").with(
                div().withClass("col-6").with(
                        label("Min"),br(),input().withId(attrName+SimilarPatentServer.LINE_CHART_MIN).withName(attrName+SimilarPatentServer.LINE_CHART_MIN).withType("number").withClass("form-control")
                ), div().withClass("col-6").with(
                        label("Max"),br(),input().withId(attrName+SimilarPatentServer.LINE_CHART_MAX).withName(attrName+SimilarPatentServer.LINE_CHART_MAX).withType("number").withClass("form-control")
                )
        );
    }

    @Override
    public String getType() {
        return "line";
    }


    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(this.attrNames!=null) {
            this.attrNames.forEach(attr -> {
                Number min = SimilarPatentServer.extractInt(params, attr+SimilarPatentServer.LINE_CHART_MIN, null);
                Number max = SimilarPatentServer.extractInt(params, attr+SimilarPatentServer.LINE_CHART_MAX, null);
                if(min!=null) attrToMinMap.put(attr,min);
                if(max!=null) attrToMaxMap.put(attr,max);
            });
        }
    }
    
    @Override
    public List<? extends AbstractChart> create(PortfolioList portfolioList, int i) {
        return Stream.of(attrNames.get(i)).map(attribute->{
            String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attribute);
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanAttr + " Timeline";
            String xAxisSuffix = "";
            String yAxisSuffix = "";
            return new LineChart(title, collectTimelineData(portfolioList.getItemList(), attribute, humanSearchType),xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType,  0, attrToMinMap.get(attribute), attrToMaxMap.get(attribute));
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
                } else if (r.toString().contains(DataSearcher.ARRAY_SEPARATOR)) {
                    return Stream.of(r.toString().split(DataSearcher.ARRAY_SEPARATOR));
                } else {
                    return Stream.of(r);
                }
            }
            return Stream.empty();
        }).map(item->{
            //System.out.println("timeline date attr: "+item);
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
