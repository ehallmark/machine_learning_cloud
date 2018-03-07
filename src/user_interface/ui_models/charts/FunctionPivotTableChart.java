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
public class FunctionPivotTableChart extends AbstractPivotChart {
    public FunctionPivotTableChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttrs, Collection<AbstractAttribute> numericAttrs) {
        super(attributes, groupedByAttrs, numericAttrs, CollectorType.Sum, Constants.PIVOT_FUNCTION_TABLE_CHART);
    }

    @Override
    protected Function<String, ContainerTag> getCombineByTagFunction(Map<String, List<String>> groupedGroupAttrs) {
        return attrName -> {
            return div().withClass("row").with(
                    div().withClass("col-8").with(
                            label("Collect By"),br(),
                            SimilarPatentServer.technologySelectWithCustomClass(getCollectByAttrFieldName(null),getCollectByAttrFieldName(null),"single-select2",groupedGroupAttrs,"")
                    ),div().withClass("col-4").with(
                            label("Collecting Function"),br(),
                            select().withClass("single-select2").withName(getCollectTypeFieldName(null)).withId(getCollectTypeFieldName(null)).with(
                                    option(CollectorType.Sum.toString()).withValue(""),
                                    option(CollectorType.Average.toString()).withValue(CollectorType.Average.toString()),
                                    option(CollectorType.Max.toString()).withValue(CollectorType.Max.toString()),
                                    option(CollectorType.Min.toString()).withValue(CollectorType.Min.toString())
                            )
                    )
            );
        };
    }

    @Override
    public TableAttribute dup() {
        return new FunctionPivotTableChart(attributes, groupByAttributes,collectByAttrs);
    }

    @Override
    public String getType() {
        return "pivotFunctionTable";
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(attrNames!=null&attrNames.size()>0&&collectByAttrName==null) throw new RuntimeException("Must select 'Collect By' attribute in "+SimilarPatentServer.humanAttributeFor(getName()));
    }
}
