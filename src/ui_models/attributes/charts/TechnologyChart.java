package ui_models.attributes.charts;

import com.googlecode.wickedcharts.highcharts.options.AxisType;
import highcharts.AbstractChart;
import highcharts.HighchartDataAdapter;
import highcharts.LineChart;
import highcharts.PieChart;
import ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyChart extends ChartAttribute {
    @Override
    public AbstractChart create(PortfolioList portfolioList) {
        return new PieChart("Technology Distribution", HighchartDataAdapter.collectSimilarityData("Technology Distribution", portfolioList));
    }
}
