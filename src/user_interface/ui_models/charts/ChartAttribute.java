package user_interface.ui_models.charts;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.span;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class ChartAttribute extends AbstractChartAttribute {

    public ChartAttribute(Collection<AbstractAttribute> attributes, String name) {
        super(attributes,name);
    }

    public abstract List<? extends AbstractChart> create(PortfolioList portfolioList, int i);

}
