package ui_models.attributes.charts;

import com.googlecode.wickedcharts.highcharts.options.AxisType;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import highcharts.AbstractChart;
import highcharts.HighchartDataAdapter;
import highcharts.LineChart;
import highcharts.PieChart;
import seeding.Constants;
import ui_models.portfolios.PortfolioList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyChart extends ChartAttribute {

    @Override
    public AbstractChart create(PortfolioList portfolioList) {
        return new PieChart("Technology Distribution", collectTechnologyData(portfolioList, "Technology Distribution", 5));
    }

    private static List<Series<?>> collectTechnologyData(PortfolioList portfolio, String seriesName, int limit) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(seriesName);

        portfolio.getItemList().stream().map(item->{
            return (String)item.getData(Constants.TECHNOLOGY);
        }).collect(Collectors.groupingBy(t->t,Collectors.counting())).entrySet().forEach(e->{
            String tech = e.getKey();
            double prob = e.getValue();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });

        data.add(series);
        //applySoftMax(data);
        return data;
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(Constants.TECHNOLOGY);
    }
}
