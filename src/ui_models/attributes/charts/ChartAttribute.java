package ui_models.attributes.charts;

import highcharts.AbstractChart;
import spark.Request;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.attributes.DependentAttribute;

/*
 * Created by Evan on 6/17/2017.
 */
public interface ChartAttribute extends DependentAttribute {
    AbstractChart create(PortfolioList portfolioList);
}
