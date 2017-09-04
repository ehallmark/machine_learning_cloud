package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.ColumnChart;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import models.value_models.*;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.select;
import static j2html.TagCreator.span;

/**
 * Created by Evan on 6/18/2017.
 */
public class AbstractHistogramChart extends ChartAttribute {
    protected String groupedBy;
    protected Collection<String> searchTypes;
    protected static final double MIN = ValueMapNormalizer.DEFAULT_START;
    protected static final double MAX = ValueMapNormalizer.DEFAULT_END;

    public AbstractHistogramChart() {
        super(Arrays.asList(Constants.AI_VALUE,Constants.SIMILARITY,Constants.REMAINING_LIFE));
    }

    @Override
    public ChartAttribute dup() {
        return new AbstractHistogramChart();
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                label("Group By"),br(),select().withId(SimilarPatentServer.CHARTS_GROUPED_BY_FIELD.replaceAll("[\\[\\]]","")).withClass("form-control single-select2").withName(SimilarPatentServer.CHARTS_GROUPED_BY_FIELD).with(
                        option("No Group (default)").attr("selected","selected").withValue(""),
                        span().with(
                                Arrays.asList(Constants.LATEST_ASSIGNEE,Constants.ASSIGNEE, Constants.TECHNOLOGY, Constants.WIPO_TECHNOLOGY).stream()
                                        .map(key->option(SimilarPatentServer.humanAttributeFor(key)).withValue(key)).collect(Collectors.toList())
                        )
                ),SimilarPatentServer.technologySelect(Constants.HISTOGRAM,getAttributes())
        );
    }

    @Override
    public String getType() {
        return "histogram";
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attributes = SimilarPatentServer.extractArray(params, Constants.HISTOGRAM);
        searchTypes = SimilarPatentServer.extractArray(params, Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
        groupedBy = SimilarPatentServer.extractString(params, SimilarPatentServer.CHARTS_GROUPED_BY_FIELD, null);
    }

    @Override
    public List<? extends AbstractChart> create(PortfolioList portfolioList, int i) {
        return Stream.of(attributes.get(i)).flatMap(attribute->{
            String humanAttr = SimilarPatentServer.humanAttributeFor(attribute);
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanAttr + " Histogram";
            double min = MIN;
            double max = MAX;
            int nBins = 5;
            String xAxisSuffix = "%";
            String yAxisSuffix = "";
            if(attribute.equals(Constants.REMAINING_LIFE)) {
                // slightly change params
                nBins = 4;
                max = 20;
                xAxisSuffix = " Years";
            }
            List<String> categories = new ArrayList<>();
            int step = (int) Math.round((max-min)/nBins);
            for(int j = 0; j < max; j += step) {
                categories.add(String.valueOf(j) + "-" + String.valueOf(j+step));
            }
            final double _min = min;
            final double _max = max;
            final int _nBins = nBins;
            final String _xAxisSuffix = xAxisSuffix;
            return portfolioList.groupedBy(groupedBy).map(groupPair->{
                String name = groupPair.getFirst();
                PortfolioList group = groupPair.getSecond();
                return new ColumnChart(title, collectDistributionData(Arrays.asList(group.getItemList()),_min,_max,_nBins, attribute, title, categories), 0d, null, _xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, name,  0,categories);
            });
        }).collect(Collectors.toList());
    }

    protected static String combineTypesToString(Collection<String> types) {
        if(types.isEmpty()) return "";
        types = types.stream().map(type-> SimilarPatentServer.humanAttributeFor(type)).collect(Collectors.toList());
        return String.join(" and ", types);
    }

    private List<Series<?>> collectDistributionData(Collection<Item> data, double min, double max, int nBins, String attribute, String title, List<String> categories) {
        List<Pair<Item,Number>> scores = data.stream().map(item->{
            Object d = item.getData(attribute);
            if(d!=null) return new Pair<>(item,(Number) d);
            else return null;
        }).filter(d->d!=null).collect(Collectors.toList());
        double step = (max-min)/nBins;
        List<Range> ranges = new ArrayList<>();
        for(double start = min; start < max; start += step) {
            ranges.add(new Range(start,start+step));
        }
        List<Series<?>> seriesList = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(title);
        series.setShowInLegend(false);
        Map<Range,Long> countMap = scores.stream().map(score->ranges.stream().filter(range->range.contains(score.getSecond().doubleValue())).findAny().orElse(null)).filter(range->range!=null).collect(Collectors.groupingBy(r->r,Collectors.counting()));
        AtomicInteger index = new AtomicInteger(0);
        ranges.forEach(range->{
            Point point = new Point(categories.get(index.getAndIncrement()), countMap.containsKey(range)?countMap.get(range):0);
            series.addPoint(point);
        });
        seriesList.add(series);
        return seriesList;
    }

    class Range {
        private double start;
        private double end;
        Range(double start, double end) {
            this.start=start;
            this.end=end;
        }
        boolean contains(double score) {
            return score > start && score <= end;
        }
    }
}
