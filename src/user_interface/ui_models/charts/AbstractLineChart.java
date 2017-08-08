package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import j2html.tags.Tag;
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

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/18/2017.
 */
public class AbstractLineChart implements ChartAttribute {
    protected List<String> attributes;
    protected Collection<String> searchTypes;

    @Override
    public Tag getOptionsTag() {
        return div().with(
                SimilarPatentServer.technologySelect(Constants.LINE_CHART,Arrays.asList(Constants.PUBLICATION_DATE,Constants.EXPIRATION_DATE,Constants.PRIORITY_DATE))
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attributes = SimilarPatentServer.extractArray(params, Constants.LINE_CHART);
        searchTypes = SimilarPatentServer.extractArray(params, SimilarPatentServer.SEARCH_TYPE_ARRAY_FIELD);
        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
    }

    @Override
    public Collection<String> getPrerequisites() {
        return attributes;
    }

    @Override
    public List<? extends AbstractChart> create(PortfolioList portfolioList) {
        return attributes.stream().map(attribute->{
            String humanAttr = SimilarPatentServer.humanAttributeFor(attribute);
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanAttr + " Timeline";
            String xAxisSuffix = "";
            String yAxisSuffix = "";
            return new LineChart(title, collectTimelineData(Arrays.asList(portfolioList.getItemList()), attribute),xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType,  0);
        }).collect(Collectors.toList());
    }

    protected static String combineTypesToString(Collection<String> types) {
        if(types.isEmpty()) return "";
        types = types.stream().map(type-> SimilarPatentServer.humanAttributeFor(type)).collect(Collectors.toList());
        return String.join(" and ", types);
    }

    private List<Series<?>> collectTimelineData(Collection<Item> data, String attribute) {
        PointSeries series = new PointSeries();
        Map<Integer,Long> dataMap = data.stream().filter(item->item.getData(attribute)!=null).collect(Collectors.groupingBy(item->Integer.valueOf(item.getData(attribute).toString().substring(0,4)),Collectors.counting()));
        dataMap.entrySet().stream().sorted(Comparator.comparing(e->e.getKey())).forEach(e ->{
            series.addPoint(new Point(e.getKey(),e.getValue()));
        });
        return Arrays.asList(
            series
        );
    }
}
