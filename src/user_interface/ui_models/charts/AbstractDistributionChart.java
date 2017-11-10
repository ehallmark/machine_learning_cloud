package user_interface.ui_models.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.PieChart;
import j2html.tags.Tag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import spark.Request;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractDistributionChart extends ChartAttribute {
    protected Collection<String> searchTypes;

    public AbstractDistributionChart() {
        super(Arrays.asList(Constants.TECHNOLOGY,Constants.WIPO_TECHNOLOGY,Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE), Constants.PIE_CHART);
    }

    @Override
    public ChartAttribute dup() {
        return new AbstractDistributionChart();
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return SimilarPatentServer.technologySelect(Constants.PIE_CHART,getAttributes());

    }

    @Override
    public String getType() {
        return "pie";
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attributes = SimilarPatentServer.extractArray(params, Constants.PIE_CHART);
        searchTypes = SimilarPatentServer.extractArray(params, Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
    }

    @Override
    public List<? extends AbstractChart> create(PortfolioList portfolioList, int i) {
        return Stream.of(attributes.get(i)).map(attribute-> {
            String title = SimilarPatentServer.humanAttributeFor(attribute) + " Distribution";
            return new PieChart(title, collectDistributionData(portfolioList, attribute, title), combineTypesToString(searchTypes));
        }).collect(Collectors.toList());
    }

    private List<Series<?>> collectDistributionData(PortfolioList portfolio, String attribute, String title) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(title);

        if(portfolio.getItemList().size()==0) return Collections.emptyList();

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
            if(tech.isEmpty()) tech = "Unknown";
            double prob = e.getSecond();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });

        data.add(series);
        return data;
    }
}
