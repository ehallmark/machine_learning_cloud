package user_interface.ui_models.charts;

import j2html.tags.ContainerTag;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 12/16/2017.
 */
public class GroupedCountTableChart extends AbstractGroupedChart {


    public GroupedCountTableChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttrs, Collection<AbstractAttribute> discreteAttributes) {
        super(attributes, groupedByAttrs, discreteAttributes, CollectorType.Count, Constants.GROUPED_TABLE_CHART);
    }

    @Override
    protected Function<String, ContainerTag> getCombineByTagFunction(Map<String, List<String>> groupedGroupAttrs) {
        return attrName -> {
            return div().withClass("row").with(
                    div().withClass("col-8").with(
                            label("Collect By"),br(),
                            SimilarPatentServer.technologySelectWithCustomClass(getCollectByAttrFieldName(null),getCollectByAttrFieldName(null),"single-select2",groupedGroupAttrs,"Asset Number (default)")
                    ),div().withClass("col-4").with(
                            label("Collecting Function"),br(),
                            select().withClass("single-select2").withName(getCollectTypeFieldName(null)).withId(getCollectTypeFieldName(null)).with(
                                    option(GroupedFunctionTableChart.CollectorType.Count.toString()).withValue("")
                            )
                    )
            );
        };
    }

    @Override
    public TableAttribute dup() {
        return new GroupedCountTableChart(attributes, groupByAttributes,collectByAttrs);
    }

    @Override
    public String getType() {
        return "groupedCountTable";
    }



}
