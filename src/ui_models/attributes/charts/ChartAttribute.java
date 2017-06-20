package ui_models.attributes.charts;

import highcharts.AbstractChart;
import spark.Request;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.attributes.DependentAttribute;

import java.util.Collection;

/*
 * Created by Evan on 6/17/2017.
 */
public interface ChartAttribute extends DependentAttribute {
    AbstractChart create(PortfolioList portfolioList);

    @Override
    default Object attributesFor(Collection portfolio, int limit) {
        return "";
    }

    @Override
    default String getName() {
        return "";
    }
}
