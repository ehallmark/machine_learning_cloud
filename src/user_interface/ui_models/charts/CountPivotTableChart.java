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
public class CountPivotTableChart extends TableAttribute {

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

    @Override
    public List<TableResponse> createTables(PortfolioList portfolioList) {
        if(attrNames==null||attrNames.isEmpty()) return Collections.emptyList();
        System.out.println("Table attr list: "+String.join("; ",attrNames));
        return Stream.of(attrNames).flatMap(attrList->{
            List<String> humanAttrs =  attrList.stream().map(attribute->SimilarPatentServer.fullHumanAttributeFor(attribute)).collect(Collectors.toList());
            String humanSearchType = combineTypesToString(searchTypes);
            String title = (collectByAttrName==null?humanSearchType:SimilarPatentServer.fullHumanAttributeFor(collectByAttrName)) + " "+ collectorType.toString() + (humanAttrs.isEmpty() ? "" :  " by "+ (humanAttrs.isEmpty() ? "*BLANK*" : String.join(", ",humanAttrs)));
            List<String> groupedBy = attrNameToGroupByAttrNameMap.get("");
            Integer maxLimit = attrNameToMaxGroupSizeMap.get("");
            Boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault("",false);
            if(maxLimit==null) maxLimit = 1;
            return Stream.of(createHelper(portfolioList.getItemList(), groupedBy, attrList, title, null, maxLimit, includeBlank));
        }).collect(Collectors.toList());
    }

    protected TableResponse createHelper(List<Item> data, List<String> rowAttrs, List<String> columnAttrs, String yTitle, String subTitle, int maxLimit, boolean includeBlank) {
        TableResponse response = new TableResponse();
        response.type=getType();
        response.title=yTitle + (subTitle!=null&&subTitle.length()>0 ? (" (Grouped by "+subTitle+")") : "");
        response.headers = new ArrayList<>();
        response.headers.addAll(columnAttrs);
        response.headers.add(collectorType.toString());
        response.numericAttrNames = Collections.singleton(collectorType.toString());
        response.computeAttributesTask = new RecursiveTask<List<Map<String,String>>>() {
            @Override
            protected List<Map<String,String>> compute() {
                if(data.size()==0) return Collections.emptyList();

                List<Pair<Item,DeepList<Object>>> columnGroups = groupTableData(data,columnAttrs);
                List<Pair<Item,DeepList<Object>>> rowGroups = groupTableData(data,rowAttrs);

                if(columnGroups.isEmpty() || rowGroups.isEmpty()) return Collections.emptyList();

                System.out.println("Starting to group table...");
                Collector<Pair<Item,DeepList<Object>>,?,? extends Number> collector = getCollectorFromCollectorType();

                // build matrix
                List<Pair<DeepList<Object>,Set<Item>>> columnData = collectData(columnGroups, maxLimit);
                List<Pair<DeepList<Object>,Set<Item>>> rowData = collectData(rowGroups, -1);

                List<Map<String,String>> data = new ArrayList<>();

                rowData.forEach(rowPair->{
                    if(includeBlank || !rowPair.getFirst().stream().anyMatch(p->p==null||p.toString().length()==0)) {
                        Map<String, String> row = new HashMap<>();
                        Set<Item> rowSet = rowPair.getSecond();
                        columnData.forEach(colPair -> {
                            if(includeBlank || !colPair.getFirst().stream().anyMatch(p->p==null||p.toString().length()==0)) {
                                List<Item> intersection = rowSet.stream().filter(item -> colPair.getSecond().contains(item)).collect(Collectors.toList());
                                Number value = intersection.stream().map(item -> new Pair<>(item, new DeepList<>())).collect(collector);
                                row.put(String.join("; ", colPair.getFirst().stream().map(i -> i.toString()).collect(Collectors.toList())), value == null ? "" : value.toString());
                            }
                        });
                    }
                });

                return data;
            }
        };
        response.computeAttributesTask.fork();
        return response;
    }



}
