package ui_models.attributes.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import highcharts.AbstractChart;
import highcharts.PieChart;
import j2html.tags.Tag;
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
    protected String attribute;
    protected String title;

    @Override
    public Tag getOptionsTag() {
        return select().withClass("form-control").withName(Constants.PIE_CHART).with(
                option("Gather Technology").withValue(Constants.TECHNOLOGY).attr("selected","selected"),
                option("WIPO Technology").withValue(Constants.WIPO_TECHNOLOGY),
                option("Company").withValue(Constants.ASSIGNEE)
        );
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        this.attribute = SimilarPatentServer.extractString(params, Constants.PIE_CHART, null);
        if(attribute!=null)this.title = SimilarPatentServer.humanAttributeFor(attribute) + " Distribution";
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(attribute);
    }

    @Override
    public AbstractChart create(PortfolioList portfolioList) {
        return new PieChart(title, collectDistributionData(portfolioList));
    }

    private List<Series<?>> collectDistributionData(PortfolioList portfolio) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(title);

        if(portfolio.getItemList().isEmpty()) return Collections.emptyList();

        List<String> items = portfolio.getItemList().stream().map(item->{
            return (String)item.getData(attribute);
        }).filter(attr->attr!=null).collect(Collectors.toList());

        if(items.isEmpty()) return Collections.emptyList();

        items.stream().collect(Collectors.groupingBy(t->t,Collectors.counting())).entrySet().stream().sorted((e1, e2)->e2.getValue().compareTo(e1.getValue())).forEach(e->{
            String tech = e.getKey();
            double prob = e.getValue();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });

        data.add(series);
        return data;
    }
}
