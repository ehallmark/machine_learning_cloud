package user_interface.ui_models.charts;

import data_pipeline.helpers.Function2;
import elasticsearch.DataSearcher;
import j2html.tags.Tag;
import model.nodes.FactorNode;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.tables.DeepList;
import user_interface.ui_models.charts.tables.TableResponse;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 12/16/2017.
 */
public class GroupedFunctionTableChart extends TableAttribute {
    public GroupedFunctionTableChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttrs, Collection<AbstractAttribute> numericAttrs) {
        super(attributes, groupedByAttrs, numericAttrs, CollectorType.Sum, Constants.GROUPED_FUNCTION_TABLE_CHART);
    }

    @Override
    protected Function<String, Tag> getCombineByTagFunction(Map<String, List<String>> groupedGroupAttrs) {
        return attrName -> {
            return div().withClass("row").with(
                    div().withClass("col-8").with(
                            label("Collect By"),br(),
                            SimilarPatentServer.technologySelectWithCustomClass(getCollectByAttrFieldName(null),getCollectByAttrFieldName(null),"single-select2",groupedGroupAttrs,"")
                    ),div().withClass("col-4").with(
                            label("Collecting Function"),br(),
                            select().withClass("single-select2").withName(getCollectTypeFieldName(null)).withId(getCollectTypeFieldName(null)).with(
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
