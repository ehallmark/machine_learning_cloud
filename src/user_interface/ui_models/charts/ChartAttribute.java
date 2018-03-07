package user_interface.ui_models.charts;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.List;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class ChartAttribute extends AbstractChartAttribute {
    public ChartAttribute(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, String name, boolean groupsPlottableOnSameChart) {
        super(attributes,groupByAttrs,name,true, groupsPlottableOnSameChart);
    }

    public abstract List<? extends AbstractChart> create(PortfolioList portfolioList, int i);


}
