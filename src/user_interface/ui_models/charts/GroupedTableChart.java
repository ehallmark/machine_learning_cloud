package user_interface.ui_models.charts;

import elasticsearch.DataSearcher;
import j2html.tags.Tag;
import model.nodes.FactorNode;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.LineChart;
import user_interface.ui_models.charts.tables.DeepList;
import user_interface.ui_models.charts.tables.TableResponse;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static j2html.TagCreator.*;
import static j2html.TagCreator.option;
import static j2html.TagCreator.span;

/**
 * Created by Evan on 12/16/2017.
 */
public class GroupedTableChart extends TableAttribute {
    protected Collection<String> searchTypes;
    public GroupedTableChart(List<AbstractAttribute> attributes) {
        super(attributes, Constants.GROUPED_TABLE_CHART);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attrNames = SimilarPatentServer.extractArray(params, Constants.GROUPED_TABLE_CHART);
        attrNames = attrNames.stream().flatMap(attr->{
            List<String> children = SimilarPatentServer.extractArray(params, attr+"[]");
            if(children.size()>0) return children.stream();
            else return Stream.of(attr);
        }).collect(Collectors.toList());

        searchTypes = SimilarPatentServer.extractArray(params, Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
    }



    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        String styleString = "margin-left: 5%; margin-right: 5%; display: none;";
        String name = getFullName().replace(".","");
        return div().with(
                div().with(
                        SimilarPatentServer.technologySelectWithCustomClass(name+(name.endsWith("[]")?"":"[]"),"nested-filter-select", getOptgroups(userRoleFunction))
                ), div().withClass("nested-form-list").with(
                        attributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getRootName())).map(filter->{
                            String collapseId = "collapse-filters-"+filter.getFullName().replaceAll("[\\[\\]]","");
                            return div().attr("style", styleString).with(
                                    SimilarPatentServer.createAttributeElement(filter.getFullName(),filter.getRootName(),collapseId,filter.getOptionsTag(userRoleFunction), filter.isNotYetImplemented(), filter.getDescription().render())
                            );
                        }).collect(Collectors.toList())
                )
        );
    }

    @Override
    public List<TableResponse> createTables(PortfolioList portfolioList) {
        return Stream.of(attrNames).map(attrList->{
            List<String> humanAttrs =  attrList.stream().map(attribute->SimilarPatentServer.fullHumanAttributeFor(attribute)).collect(Collectors.toList());
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanSearchType+" Counts by "+String.join(", ",humanAttrs);
            return createHelper(portfolioList.getItemList(),attrList,title);
        }).collect(Collectors.toList());
    }

    @Override
    public TableAttribute dup() {
        return new GroupedTableChart(attributes);
    }

    @Override
    public String getType() {
        return "groupedTable";
    }


    private TableResponse createHelper(List<Item> data, List<String> attrList, String yTitle) {
        TableResponse response = new TableResponse();
        response.type=getType();
        response.title=yTitle;
        response.headers = new ArrayList<>();
        response.headers.addAll(attrList);
        response.headers.add("count");
        response.computeAttributesTask = new RecursiveTask<List<Map<String,String>>>() {
            @Override
            protected List<Map<String,String>> compute() {
                if(data.size()==0) return Collections.emptyList();
                String[] attrsArray = attrList.toArray(new String[]{});
                List<DeepList<Object>> items = (List<DeepList<Object>>)data.stream().flatMap(item-> {
                    List<List<?>> rs = attrList.stream().map(attribute-> {
                        Object r = item.getData(attribute);
                        if (r != null) {
                            if (r instanceof Collection) {
                                return (List<?>) ((Collection)r).stream().collect(Collectors.toList());
                            } else if (r.toString().contains(DataSearcher.ARRAY_SEPARATOR)) {
                                return Arrays.asList(r.toString().split(DataSearcher.ARRAY_SEPARATOR));
                            } else {
                                return Collections.singletonList(r);
                            }
                        }
                        return Collections.emptyList();
                    }).collect(Collectors.toList());
                    FactorNode factor = new FactorNode(null,attrsArray,rs.stream().mapToInt(r->Math.max(1,r.size())).toArray());
                    return factor.assignmentPermutationsStream().map(assignment->{
                         return new DeepList<>(
                                IntStream.range(0,assignment.length).mapToObj(i->{
                                    if(i>=rs.size()) System.out.println("WARNING 1: "+factor.toString());
                                    List<?> r = rs.get(i);
                                    return r.size()>0?r.get(assignment[i]):"";
                                }).collect(Collectors.toList())
                        );
                    });
                }).collect(Collectors.toList());

                if(items.isEmpty()) return Collections.emptyList();

                System.out.println("Starting to group table...");
                return items.stream()
                        .collect(Collectors.groupingBy(t->t,Collectors.counting()))
                        .entrySet().stream().sorted((e1, e2)->e2.getValue().compareTo(e1.getValue()))
                        .map(e->{
                            Map<String,String> row = Collections.synchronizedMap(new HashMap<>());
                            for(int i = 0; i < attrList.size(); i++) {
                                if(i>=e.getKey().size()) System.out.println("WARNING 2: "+e.getKey()+"  ->  "+attrList);
                                row.put(attrList.get(i),e.getKey().get(i).toString());
                            }
                            row.put("count",e.getValue().toString());
                            return row;
                        }).collect(Collectors.toList());

            }
        };
        response.computeAttributesTask.fork();
        return response;
    }
}
