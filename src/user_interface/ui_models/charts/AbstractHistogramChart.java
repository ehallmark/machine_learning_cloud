package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.ColumnChart;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 6/18/2017.
 */
public class AbstractHistogramChart extends ChartAttribute {
    private Map<String,RangeAttribute> nameToRangeMap;
    public AbstractHistogramChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs) {
        this(attributes, groupByAttrs, attributes.stream().collect(Collectors.toMap(attr->attr.getFullName(),attr->(RangeAttribute)attr)));
    }

    private AbstractHistogramChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Map<String,RangeAttribute> nameToRangeMap) {
        super(attributes,groupByAttrs,Constants.HISTOGRAM, true);
        this.nameToRangeMap=nameToRangeMap;
    }


    @Override
    public ChartAttribute dup() {
        return new AbstractHistogramChart(attributes,groupByAttributes,nameToRangeMap);
    }


    @Override
    public String getType() {
        return "histogram";
    }


    @Override
    public List<? extends AbstractChart> create(PortfolioList portfolioList, int i) {
        return Stream.of(attrNames.get(i)).flatMap(attribute->{
            String humanAttr = SimilarPatentServer.humanAttributeFor(attribute);
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanAttr + " Histogram";


            RangeAttribute rangeAttribute = nameToRangeMap.get(attribute);
            double min = rangeAttribute.min().doubleValue();
            double max = rangeAttribute.max().doubleValue();
            int nBins = rangeAttribute.nBins();
            String xAxisSuffix = rangeAttribute.valueSuffix();
            String yAxisSuffix = "";

            List<String> categories = new ArrayList<>();
            int step = (int) Math.round((max-min)/nBins);
            for(int j = 0; j < max; j += step) {
                categories.add(String.valueOf(j) + "-" + String.valueOf(j+step));
            }
            final double _min = min;
            final double _max = max;
            final int _nBins = nBins;
            final String _xAxisSuffix = xAxisSuffix;

            boolean plotGroupsOnSameChart = groupsPlottableOnSameChart && attrToPlotOnSameChartMap.getOrDefault(attribute, false);
            System.out.println("Plotting "+attribute+" groups on same chart: "+plotGroupsOnSameChart);
            if(plotGroupsOnSameChart) {
                List<Series<?>> seriesList = groupPortfolioListForGivenAttribute(portfolioList, attribute).flatMap(groupPair -> {
                    return collectDistributionData(groupPair.getSecond().getItemList(), _min, _max, _nBins, attribute, SimilarPatentServer.humanAttributeFor(groupPair.getFirst()), categories).stream();
                }).collect(Collectors.toList());
                return Stream.of(new ColumnChart(title, seriesList, 0d, null, _xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, null, 0, categories));
            } else {
                return groupPortfolioListForGivenAttribute(portfolioList, attribute).map(groupPair -> {
                    String name = groupPair.getFirst();
                    PortfolioList group = groupPair.getSecond();
                    return new ColumnChart(title, collectDistributionData(group.getItemList(), _min, _max, _nBins, attribute, title, categories), 0d, null, _xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, name, 0, categories);
                });
            }
        }).collect(Collectors.toList());
    }

    private List<Series<?>> collectDistributionData(Collection<Item> data, double min, double max, int nBins, String attribute, String title, List<String> categories) {
        List<Pair<Item,Number>> scores = data.stream().map(item->{
            Object d = item.getData(attribute);
            if(d!=null) return new Pair<>(item,(Number) d);
            else return null;
        }).filter(d->d!=null).collect(Collectors.toList());
        double step = (max-min)/nBins;
        List<Range> ranges = new ArrayList<>();
        int i = 0;
        for(double start = min; start < max; start += step) {
            ranges.add(new Range(start,start+step, i == nBins-1));
            i++;
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
        private boolean inclusive;
        Range(double start, double end, boolean inclusive) {
            this.start=start;
            this.inclusive=inclusive;
            this.end=end;
        }
        boolean contains(double score) {
            return score >= start && (inclusive ? score <= end : score < end);
        }
    }
}
