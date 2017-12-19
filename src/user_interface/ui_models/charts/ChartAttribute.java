package user_interface.ui_models.charts;

import data_pipeline.helpers.Function2;
import j2html.tags.Tag;
import user_interface.server.SimilarPatentServer;
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
        if(groupsPlottableOnSameChart) {
            Function<String,Tag> plottableByGroupsFunction = this::getAdditionalTagPerAttr;
            newTagFunction = additionalTagFunction == null ? plottableByGroupsFunction : attrName -> combineTagFunction.apply(plottableByGroupsFunction.apply(attrName),additionalTagFunction.apply(attrName));
            Function<String, List<String>> additionalIdsFunction = attrName -> {
                String groupById = getGroupByChartFieldName(idFromName(attrName));
                String id = groupById + PLOT_GROUPS_ON_SAME_CHART_FIELD;
                return Collections.singletonList(id);
            };
            newAdditionalIdsFunction = additionalInputIdsFunction == null ? additionalIdsFunction : attrName -> {
                return Stream.of(additionalIdsFunction.apply(attrName), additionalInputIdsFunction.apply(attrName)).flatMap(list -> list.stream()).collect(Collectors.toList());
            };
        } else {
            newTagFunction = additionalTagFunction;
            newAdditionalIdsFunction = additionalInputIdsFunction;
        }
        return super.getOptionsTag(userRoleFunction,newTagFunction,newAdditionalIdsFunction,combineTagFunction,perAttr);
    }

    private Tag getAdditionalTagPerAttr(String attrName) {
        attrName = idFromName(attrName);
        return div().withClass("row").with(
                div().withClass("col-6").with(
                        label("Min Date"),br(),input().withId(attrName+ SimilarPatentServer.LINE_CHART_MIN).withName(attrName+SimilarPatentServer.LINE_CHART_MIN).withType("text").withClass("datepicker form-control")
                ), div().withClass("col-6").with(
                        label("Max Date"),br(),input().withId(attrName+SimilarPatentServer.LINE_CHART_MAX).withName(attrName+SimilarPatentServer.LINE_CHART_MAX).withType("text").withClass("datepicker form-control")
                )
        );
    }
}
