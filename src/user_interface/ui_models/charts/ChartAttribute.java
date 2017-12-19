package user_interface.ui_models.charts;

import data_pipeline.helpers.Function2;
import j2html.tags.Tag;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class ChartAttribute extends AbstractChartAttribute {
    public static final String PLOT_GROUPS_ON_SAME_CHART_FIELD = "plotGroupsSameChart";
    protected boolean groupsPlottableOnSameChart;
    public ChartAttribute(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, String name, boolean groupsPlottableOnSameChart) {
        super(attributes,groupByAttrs,name,true);
        this.groupsPlottableOnSameChart=groupsPlottableOnSameChart;
    }

    public abstract List<? extends AbstractChart> create(PortfolioList portfolioList, int i);

    @Override
    protected Tag getOptionsTag(Function<String,Boolean> userRoleFunction, Function<String,Tag> additionalTagFunction, Function<String,List<String>> additionalInputIdsFunction, Function2<Tag,Tag,Tag> combineTagFunction, boolean perAttr) {
        Function<String,Tag> newTagFunction;
        Function<String,List<String>> newAdditionalIdsFunction;
        Function2<Tag,Tag,Tag> newCombineByFunction;
        if(groupsPlottableOnSameChart) {
            Function<String,Tag> plottableByGroupsFunction = this::getAdditionalTagPerAttr;
            newTagFunction = additionalTagFunction == null ? plottableByGroupsFunction : attrName -> combineTagFunction.apply(plottableByGroupsFunction.apply(attrName),additionalTagFunction.apply(attrName));
            Function<String, List<String>> additionalIdsFunction = attrName -> {
                return Collections.singletonList(getGroupByChartFieldName(idFromName(attrName)) + PLOT_GROUPS_ON_SAME_CHART_FIELD);
            };
            newAdditionalIdsFunction = additionalInputIdsFunction == null ? additionalIdsFunction : attrName -> {
                return Stream.of(additionalIdsFunction.apply(attrName), additionalInputIdsFunction.apply(attrName)).flatMap(list -> list.stream()).collect(Collectors.toList());
            };
            newCombineByFunction = (tag1,tag2)-> div().withClass("row").with(
                    div().withClass("col-10").with(tag1),
                    div().withClass("col-2").with(tag2)
            );
        } else {
            newTagFunction = additionalTagFunction;
            newAdditionalIdsFunction = additionalInputIdsFunction;
            newCombineByFunction = combineTagFunction;
        }
        return super.getOptionsTag(userRoleFunction,newTagFunction,newAdditionalIdsFunction,newCombineByFunction,perAttr);
    }

    private Tag getAdditionalTagPerAttr(String attrName) {
        attrName = getGroupByChartFieldName(idFromName(attrName));
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        label("Plot Groups Together").attr("title","Plot groups together on the same chart.").with(
                                input().withId(attrName+ PLOT_GROUPS_ON_SAME_CHART_FIELD).withName(attrName+PLOT_GROUPS_ON_SAME_CHART_FIELD).withType("checkbox").withClass("form-control")
                        )
                )
        );
    }
}
