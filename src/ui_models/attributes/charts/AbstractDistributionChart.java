package ui_models.attributes.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import highcharts.AbstractChart;
import highcharts.PieChart;
import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import seeding.Constants;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractDistributionChart implements ChartAttribute {
    protected List<String> attributes;

    @Override
    public Tag getOptionsTag() {
        return SimilarPatentServer.technologySelect(Constants.PIE_CHART,Arrays.asList(Constants.TECHNOLOGY,Constants.WIPO_TECHNOLOGY,Constants.ASSIGNEE));

    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        this.attributes = SimilarPatentServer.extractArray(params, Constants.PIE_CHART);
    }

    @Override
    public Collection<String> getPrerequisites() {
        return attributes;
    }

    @Override
    public List<? extends AbstractChart> create(PortfolioList portfolioList) {
        return attributes.stream().map(attribute-> {
            String title = SimilarPatentServer.humanAttributeFor(attribute) + " Distribution";
            return new PieChart(title, collectDistributionData(portfolioList, attribute, title));
        }).collect(Collectors.toList());
    }

    private List<Series<?>> collectDistributionData(PortfolioList portfolio, String attribute, String title) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(title);

        if(portfolio.getItemList().isEmpty()) return Collections.emptyList();

        List<String> items = portfolio.getItemList().stream().map(item->{
            return (String)item.getData(attribute);
        }).filter(attr->attr!=null).collect(Collectors.toList());

        if(items.isEmpty()) return Collections.emptyList();

        int limit = 15;
        List<Pair<String,Long>> itemPairs = items.stream().collect(Collectors.groupingBy(t->t,Collectors.counting())).entrySet().stream().sorted((e1, e2)->e2.getValue().compareTo(e1.getValue())).map(e->new Pair<>(e.getKey(),e.getValue())).collect(Collectors.toList());
        if(itemPairs.size()>limit+5) {
            Pair<String,Long> remaining = itemPairs.subList(limit,itemPairs.size()).stream().reduce((p1,p2)->new Pair<>("Remaining",p1.getSecond()+p2.getSecond())).get();
            itemPairs = itemPairs.subList(0,limit);
            itemPairs.add(remaining);
        }
        itemPairs.forEach(e->{
            String tech = e.getFirst();
            double prob = e.getSecond();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });

        data.add(series);
        return data;
    }
}
