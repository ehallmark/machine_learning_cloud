package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Series;
import com.googlecode.wickedcharts.highcharts.options.series.SimpleSeries;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/18/2017.
 */
public class AbstractLineChart extends ChartAttribute {
    private static final long MILLISECONDS_PER_DAY = 1000L * 60L * 60L * 24L;

    protected Map<String,LocalDate> attrToMinMap;
    protected Map<String,LocalDate> attrToMaxMap;
    public AbstractLineChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttributes) {
        super(attributes,groupedByAttributes,Constants.LINE_CHART, true);
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
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Arrays.asList(idFromName(attrName)+SimilarPatentServer.LINE_CHART_MIN,idFromName(attrName)+SimilarPatentServer.LINE_CHART_MAX);
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,(tag1,tag2)->div().with(tag1,tag2),true);
    }


    private Tag getAdditionalTagPerAttr(String attrName) {
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


    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(this.attrNames!=null) {
            this.attrNames.forEach(attr -> {
                Object min = SimilarPatentServer.extractString(params, attr.replace(".","")+SimilarPatentServer.LINE_CHART_MIN, null);
                Object max = SimilarPatentServer.extractString(params, attr.replace(".","")+SimilarPatentServer.LINE_CHART_MAX, null);
                if(min != null && min.toString().length()>0) {
                    try {
                        min = LocalDate.parse(min.toString()).format(DateTimeFormatter.ISO_DATE);
                    } catch(Exception e) {
                        throw new RuntimeException("Error parsing date: "+min);
                    }
                    System.out.println("Found start date: "+min);
                } else {
                    min = null;
                }
                if(max != null && max.toString().length()>0) {
                    try {
                        max = LocalDate.parse(max.toString()).format(DateTimeFormatter.ISO_DATE);
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
    public List<? extends AbstractChart> create(PortfolioList portfolioList, int i) {
        return Stream.of(attrNames.get(i)).flatMap(attribute->{
            String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attribute);
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanAttr + " Timeline";
            String xAxisSuffix = "";
            String yAxisSuffix = "";
            LocalDate min = attrToMinMap.get(attribute);
            LocalDate max = attrToMaxMap.get(attribute);
            boolean plotGroupsOnSameChart = groupsPlottableOnSameChart && attrToPlotOnSameChartMap.getOrDefault(attribute, false);
            System.out.println("Plotting "+attribute+" groups on same chart: "+plotGroupsOnSameChart);
            if(plotGroupsOnSameChart) {
                List<Series<?>> seriesList = groupPortfolioListForGivenAttribute(portfolioList, attribute).flatMap(groupPair -> {
                    return collectTimelineData(groupPair.getSecond().getItemList(), attribute, SimilarPatentServer.humanAttributeFor(groupPair.getFirst()), min, max).stream();
                }).collect(Collectors.toList());
                // get actual min and max
                return Stream.of(new LineChart(title,null, seriesList, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, 0));

            } else {
                return groupPortfolioListForGivenAttribute(portfolioList, attribute).map(groupPair -> {
                    return new LineChart(title, groupPair.getFirst(), collectTimelineData(groupPair.getSecond().getItemList(), attribute, singularize(humanSearchType) + " Count", min, max), xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, 0);
                });
            }
        }).collect(Collectors.toList());
    }

    private static long millisecondsFromDate(LocalDate date) {
        ZonedDateTime zdt = LocalDateTime.of(date.getYear(),date.getMonth(),date.getDayOfMonth(),0,0,0).atZone(ZoneId.of("America/Los_Angeles"));
        return zdt.toInstant().toEpochMilli();
    }

    private List<Series<?>> collectTimelineData(Collection<Item> data, String attribute, String seriesName, LocalDate min, LocalDate max) {
        SimpleSeries series = new SimpleSeries();
        series.setName(seriesName);
        Map<LocalDate,Long> dataMap = (Map<LocalDate,Long>) data.stream().flatMap(item-> {
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
            try {
                return (LocalDate)LocalDate.parse(item.toString(), DateTimeFormatter.ISO_DATE);
            } catch(Exception e) {
                return (LocalDate)null;
            }
        }).filter(date->date!=null&&(min==null||!((LocalDate)date).isBefore(min))&&(max==null||((LocalDate)date).isBefore(max))).collect(Collectors.groupingBy(Function.identity(),Collectors.counting()));

        SortedMap<LocalDate,Long> sortedData = new TreeMap<>(dataMap);

        if(sortedData.size() == 0) return Collections.emptyList();

        LocalDate firstDate = min == null ? sortedData.firstKey() : min;
        long pointStart = millisecondsFromDate(firstDate);
        long pointInterval = MILLISECONDS_PER_DAY;
        series.setPointStart(pointStart);
        series.setPointInterval(pointInterval);

        AtomicReference<LocalDate> lastDate = new AtomicReference<>(firstDate);
        List<Number> dataPoints = sortedData.entrySet().stream().flatMap(e->{
            // catch up dates
            LocalDate date = e.getKey();
            Long count = e.getValue();
            List<Number> points = new ArrayList<>();
            while(lastDate.get().isBefore(date)) {
                lastDate.set(lastDate.get().plusDays(1));
                points.add(0);
            }
            // add date
            points.add(count);
            return points.stream();
        }).collect(Collectors.toList());
        while(max!=null && lastDate.get().isBefore(max)) {
            dataPoints.add(0);
            lastDate.set(lastDate.get().plusDays(1));
        }
        series.setData(dataPoints);
        return Arrays.asList(
            series
        );
    }

    private static String singularize(String in) {
        return in.endsWith("s") && in.length()>1 ? in.substring(0,in.length()-1) : in;
    }
}
