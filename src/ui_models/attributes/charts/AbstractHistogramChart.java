package ui_models.attributes.charts;

import highcharts.AbstractChart;
import ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by Evan on 6/18/2017.
 */
public abstract class AbstractHistogramChart implements ChartAttribute {
    protected String title;
    protected String attribute;

    public AbstractHistogramChart(String title, String attribute) {
        this.title=title;
        this.attribute=attribute;
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(attribute);
    }

    @Override
    public AbstractChart create(PortfolioList portfolioList) {
        return null;
    }



    class Histogram {
        private
    }
}
