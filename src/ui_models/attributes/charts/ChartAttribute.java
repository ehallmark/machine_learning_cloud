package ui_models.attributes.charts;

import highcharts.AbstractChart;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.attributes.DependentAttribute;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by Evan on 6/17/2017.
 */
public abstract class ChartAttribute implements DependentAttribute {
    public abstract AbstractChart create(PortfolioList portfolioList);
}
