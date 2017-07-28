package user_interface.ui_models.charts;

import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.attributes.DependentAttribute;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/*
 * Created by Evan on 6/17/2017.
 */
public interface ChartAttribute extends DependentAttribute {
    List<? extends AbstractChart> create(PortfolioList portfolioList, String groupedBy);

    @Override
    default Object attributesFor(Collection portfolio, int limit) {
        return "";
    }

    @Override
    default String getName() {
        return "";
    }
}
