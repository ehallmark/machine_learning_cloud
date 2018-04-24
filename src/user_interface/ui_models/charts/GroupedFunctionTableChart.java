package user_interface.ui_models.charts;

import j2html.tags.ContainerTag;
import seeding.Constants;
import spark.Request;
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
public class GroupedFunctionTableChart extends AbstractGroupedChart {
    public GroupedFunctionTableChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttrs, Collection<AbstractAttribute> numericAttrs) {
        super(attributes, groupedByAttrs, numericAttrs, CollectorType.Sum, Constants.GROUPED_FUNCTION_TABLE_CHART);
    }

    @Override
    protected Function<String, ContainerTag> getCombineByTagFunction(Map<String, List<String>> groupedGroupAttrs) {
        return attrName -> {
            return div().withClass("row").with(
                    div().withClass("col-8").with(
                            label("Collect By"),br(),
                            SimilarPatentServer.technologySelectWithCustomClass(getCollectByAttrFieldName(),getCollectByAttrFieldName(),"single-select2",groupedGroupAttrs,"")
                    ),div().withClass("col-4").with(
                            label("Collecting Function"),br(),
                            select().withClass("single-select2").withName(getCollectTypeFieldName()).withId(getCollectTypeFieldName()).with(
                                    option(GroupedFunctionTableChart.CollectorType.Sum.toString()).withValue(""),
                                    option(GroupedFunctionTableChart.CollectorType.Average.toString()).withValue(GroupedFunctionTableChart.CollectorType.Average.toString()),
                                    option(GroupedFunctionTableChart.CollectorType.Max.toString()).withValue(GroupedFunctionTableChart.CollectorType.Max.toString()),
                                    option(GroupedFunctionTableChart.CollectorType.Min.toString()).withValue(GroupedFunctionTableChart.CollectorType.Min.toString())
                            )
                    )
            );
        };
    }

    @Override
    public TableAttribute dup() {
        return new GroupedFunctionTableChart(attributes, groupByAttributes,collectByAttrs);
    }

    @Override
    public String getType() {
        return "groupedTable";
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(attrNames!=null&attrNames.size()>0&&collectByAttrName==null) throw new RuntimeException("Must select 'Collect By' attribute in "+SimilarPatentServer.humanAttributeFor(getName()));
    }
}
