package ui_models.attributes.charts;

import highcharts.AbstractChart;
import ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 6/17/2017.
 */
public abstract class ChartAttribute {
    public abstract AbstractChart create(PortfolioList portfolioList);
}
