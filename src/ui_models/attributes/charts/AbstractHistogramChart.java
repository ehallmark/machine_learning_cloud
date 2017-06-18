package ui_models.attributes.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import highcharts.AbstractChart;
import highcharts.ColumnChart;
import highcharts.PieChart;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.attributes.value.ValueMapNormalizer;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.option;

/**
 * Created by Evan on 6/18/2017.
 */
public class AbstractHistogramChart implements ChartAttribute {
    protected String title;
    protected String attribute;
    protected static final double MIN = ValueMapNormalizer.DEFAULT_START;
    protected static final double MAX = ValueMapNormalizer.DEFAULT_END;

    @Override
    public Tag getOptionsTag() {
        return select().withName(Constants.HISTOGRAM).with(
                SimilarPatentServer.valueModelMap.keySet().stream().map(key->{
                    return option(SimilarPatentServer.humanAttributeFor(key)).withValue(key);
                }).collect(Collectors.toList())
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        this.attribute = SimilarPatentServer.extractString(params, Constants.HISTOGRAM, null);
        if(attribute!=null)this.title = SimilarPatentServer.humanAttributeFor(attribute) + " Histogram";
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(attribute);
    }

    @Override
    public AbstractChart create(PortfolioList portfolioList) {
        return new ColumnChart(title, collectDistributionData(portfolioList.getItemList(),MIN,MAX,5), 0d, null, "", 0);
    }

    private List<Series<?>> collectDistributionData(Collection<Item> data, double min, double max, int nBins) {
        List<Pair<Item,Number>> scores = data.stream().map(item->new Pair<>(item,(Number) item.getData(attribute))).collect(Collectors.toList());
        double step = (max-min)/nBins;
        List<Range> ranges = new ArrayList<>(nBins);
        for(double start = min; start < max; start += step) {
            ranges.add(new Range(start,start+step));
        }

        List<Series<?>> seriesList = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(title);
        Map<Range,Long> countMap = scores.stream().map(score->ranges.stream().filter(range->range.contains(score.getSecond().doubleValue())).findAny().orElse(null)).filter(range->range!=null).collect(Collectors.groupingBy(r->r,Collectors.counting()));
        ranges.forEach(range->{
            Point point = new Point(range.mean(),countMap.containsKey(range)?countMap.get(range):0);
            series.addPoint(point);
        });
        seriesList.add(series);
        return seriesList;
    }

    class Range {
        private double start;
        private double end;
        Range(double start, double end) {
            this.end=end;
            this.start=start;
        }
        boolean contains(double score) {
            return (start==0d && score == 0d) || (score > start && score <= end);
        }
        double mean() {
            return (end+start)/2;
        }
    }
}
