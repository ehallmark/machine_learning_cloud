package user_interface.ui_models.charts;

import j2html.tags.ContainerTag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.tables.DeepList;
import user_interface.ui_models.charts.tables.TableResponse;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/**
 * Created by Evan on 12/16/2017.
 */
public class CountPivotTableChart extends AbstractPivotChart {

    public CountPivotTableChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttrs, Collection<AbstractAttribute> discreteAttributes) {
        super(attributes, groupedByAttrs, discreteAttributes, CollectorType.Count, Constants.PIVOT_COUNT_TABLE_CHART);
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
                                    option(CollectorType.Count.toString()).withValue("")
                            )
                    )
            );
        };
    }

    @Override
    public TableAttribute dup() {
        return new CountPivotTableChart(attributes, groupByAttributes,collectByAttrs);
    }

    @Override
    public String getType() {
        return "pivotCountTable";
    }




}