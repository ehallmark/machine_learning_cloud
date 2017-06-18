package ui_models.attributes.charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import highcharts.AbstractChart;
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
public class CompanyDistributionChart extends AbstractDistributionChart {

    public CompanyDistributionChart() {
        super("Company Distribution",Constants.ASSIGNEE);
    }
}
