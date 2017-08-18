package user_interface.ui_models.charts;

import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.attributes.DependentAttribute;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class ChartAttribute extends AbstractAttribute implements DependentAttribute {
    public ChartAttribute() {
        super(Collections.emptyList());
    }

    public abstract List<? extends AbstractChart> create(PortfolioList portfolioList);

    @Override
    public String getName() {
        throw new UnsupportedOperationException("getName not defined for charts.");
    }

    @Override
    public String getType() {
        throw new UnsupportedOperationException("getType not defined for charts.");
    }

    @Override
    public AbstractFilter.FieldType getFieldType() { throw new UnsupportedOperationException("fieldType not defined for charts.");}

}
