package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
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

import static j2html.TagCreator.option;

/**
 * Created by Evan on 6/18/2017.
 */
public class AbstractHistogramChart implements ChartAttribute {
    protected List<String> attributes;
    protected Collection<String> searchTypes;
    protected static final double MIN = ValueMapNormalizer.DEFAULT_START;
    protected static final double MAX = ValueMapNormalizer.DEFAULT_END;

    @Override
    public Tag getOptionsTag() {
        return SimilarPatentServer.technologySelect(Constants.HISTOGRAM,Arrays.asList(Constants.AI_VALUE,Constants.SIMILARITY,Constants.REMAINING_LIFE));
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attributes = SimilarPatentServer.extractArray(params, Constants.HISTOGRAM);
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
            for(int i = 0; i < max; i += step) {
                categories.add(String.valueOf(i) + "-" + String.valueOf(i+step));
            }
            return new ColumnChart(title, collectDistributionData(Arrays.asList(portfolioList.getItemList()),min,max,nBins, attribute, title, categories), 0d, null, xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType,  0,categories);
        }).collect(Collectors.toList());
    }

    protected static String combineTypesToString(Collection<String> types) {
        if(types.isEmpty()) return "";
        types = types.stream().map(type-> SimilarPatentServer.humanAttributeFor(type)).collect(Collectors.toList());
        return String.join(" and ", types);
    }

    private List<Series<?>> collectDistributionData(Collection<Item> data, double min, double max, int nBins, String attribute, String title, List<String> categories) {
        List<Pair<Item,Number>> scores = data.stream().map(item->new Pair<>(item,(Number) item.getData(attribute))).collect(Collectors.toList());
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
