package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import j2html.tags.Tag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.ColumnChart;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/18/2017.
 */
public class AbstractHistogramChart extends ChartAttribute {
    protected String groupedBy;
    protected Collection<String> searchTypes;
    Map<String,RangeAttribute> nameToRangeMap;
    public AbstractHistogramChart(Collection<AbstractAttribute> attributes) {
        this(attributes, attributes.stream().collect(Collectors.toMap(attr->attr.getFullName(),attr->(RangeAttribute)attr)));
    }

    private AbstractHistogramChart(Collection<AbstractAttribute> attributes, Map<String,RangeAttribute> nameToRangeMap) {
        super(attributes,Constants.HISTOGRAM);
        this.nameToRangeMap=nameToRangeMap;
    }


    @Override
    public ChartAttribute dup() {
        return new AbstractHistogramChart(attributes,nameToRangeMap);
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div().with(
                label("Group By"),br(),select().withId(SimilarPatentServer.CHARTS_GROUPED_BY_FIELD.replaceAll("[\\[\\]]","")).withClass("form-control single-select2").withName(SimilarPatentServer.CHARTS_GROUPED_BY_FIELD).with(
                        option("No Group (default)").attr("selected","selected").withValue(""),
                        span().with(
                                Stream.of(Constants.ASSIGNMENTS+"."+Constants.ASSIGNOR, Constants.ASSIGNMENTS+"."+Constants.ASSIGNEE, Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE, Constants.LATEST_ASSIGNEE+"."+Constants.NORMALIZED_LATEST_ASSIGNEE, Constants.TECHNOLOGY, Constants.WIPO_TECHNOLOGY)
                                        .map(key->option(SimilarPatentServer.humanAttributeFor(key)).withValue(key)).collect(Collectors.toList())
                        )
                ), super.getOptionsTag(userRoleFunction)
        );
    }

    @Override
    public String getType() {
        return "histogram";
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attrNames = SimilarPatentServer.extractArray(params, Constants.HISTOGRAM);
        searchTypes = SimilarPatentServer.extractArray(params, Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
        groupedBy = SimilarPatentServer.extractString(params, SimilarPatentServer.CHARTS_GROUPED_BY_FIELD, null);
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
            return portfolioList.groupedBy(groupedBy).sorted((p1,p2)->Integer.compare(p2.getSecond().getItemList().size(),p1.getSecond().getItemList().size())).limit(10).map(groupPair->{
                String name = groupPair.getFirst();
                PortfolioList group = groupPair.getSecond();
                return new ColumnChart(title, collectDistributionData(group.getItemList(),_min,_max,_nBins, attribute, title, categories), 0d, null, _xAxisSuffix, yAxisSuffix, humanAttr, humanSearchType, name,  0,categories);
            });
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
