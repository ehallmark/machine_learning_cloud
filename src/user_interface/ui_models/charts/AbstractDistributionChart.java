package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import elasticsearch.DataSearcher;
import j2html.tags.Tag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.PieChart;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractDistributionChart extends ChartAttribute {

    public AbstractDistributionChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs) {
        super(attributes, groupByAttrs, Constants.PIE_CHART);
    }

    @Override
    public ChartAttribute dup() {
        return new AbstractDistributionChart(attributes,groupByAttributes);
    }

    @Override
    public String getType() {
        return "pie";
    }


    @Override
    public List<? extends AbstractChart> create(PortfolioList portfolioList, int i) {
        return Stream.of(attrNames.get(i)).flatMap(attribute-> {
            String title = SimilarPatentServer.humanAttributeFor(attribute) + " Distribution";
            return groupPortfolioListForGivenAttribute(portfolioList,attribute).map(groupPair->{
                    return new PieChart(title,  groupPair.getFirst(), collectDistributionData(groupPair.getSecond(), attribute, title), combineTypesToString(searchTypes));
            });
        }).collect(Collectors.toList());
    }

    private List<Series<?>> collectDistributionData(PortfolioList portfolio, String attribute, String title) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(title);

        if(portfolio.getItemList().size()==0) return Collections.emptyList();

        List<Object> items = (List<Object>)portfolio.getItemList().stream().flatMap(item-> {
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
        }).filter(attr->attr!=null).collect(Collectors.toList());

        if(items.isEmpty()) return Collections.emptyList();

        int limit = 15;
        List<Pair<Object,Long>> itemPairs = items.stream()
                .collect(Collectors.groupingBy(t->t,Collectors.counting()))
                .entrySet().stream().sorted((e1, e2)->e2.getValue().compareTo(e1.getValue()))
                .map(e->new Pair<>(e.getKey(),e.getValue())).collect(Collectors.toList());

        if(itemPairs.size()>limit+5) {
            Pair<Object,Long> remaining = itemPairs.subList(limit,itemPairs.size()).stream().reduce((p1,p2)->new Pair<>("Remaining",p1.getSecond()+p2.getSecond())).get();
            itemPairs = itemPairs.subList(0,limit);
            itemPairs.add(remaining);
        }
        itemPairs.forEach(e->{
            String tech = e.getFirst().toString();
            if(tech.isEmpty()) tech = "Unknown";
            double prob = e.getSecond();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });

        data.add(series);
        return data;
    }
}
