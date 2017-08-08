package user_interface.ui_models.charts;

import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.attributes.DependentAttribute;

import java.util.Collection;
import java.util.List;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class ChartAttribute extends DependentAttribute {
    public abstract List<? extends AbstractChart> create(PortfolioList portfolioList);

    @Override
    public Object attributesFor(Collection portfolio, int limit) {
        throw new UnsupportedOperationException("AttributesFor not supported on charts.");
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException("getName not defined for charts.");
    }

    @Override
    public String getType() {
        throw new UnsupportedOperationException("getType not defined for charts.");
    }
}
