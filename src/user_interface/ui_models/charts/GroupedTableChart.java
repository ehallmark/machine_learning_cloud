package user_interface.ui_models.charts;

import elasticsearch.DataSearcher;
import j2html.tags.Tag;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.LineChart;
import user_interface.ui_models.charts.tables.TableResponse;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        searchTypes = SimilarPatentServer.extractArray(params, Constants.DOC_TYPE_INCLUDE_FILTER_STR);
        // what to do if not present?
        if(searchTypes.isEmpty()) {
            searchTypes = Arrays.asList(PortfolioList.Type.values()).stream().map(type->type.toString()).collect(Collectors.toList());
        }
    }



    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return technologySelect(userRoleFunction);
    }

    @Override
    public List<TableResponse> createTables(PortfolioList portfolioList, int i) {
        return Stream.of(attrNames.get(i)).map(attribute->{
            String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attribute);
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanSearchType+" Counts by "+humanAttr;
            return createHelper(portfolioList.getItemList(),attribute,title);
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


    private TableResponse createHelper(List<Item> data, String attribute, String yTitle) {
        TableResponse response = new TableResponse();
        response.type=getType();
        response.title=yTitle;
        response.headers = Arrays.asList(attribute,"count");
        response.computeAttributesTask = new RecursiveTask<List<Map<String,String>>>() {
            @Override
            protected List<Map<String,String>> compute() {
                if(data.size()==0) return Collections.emptyList();

                List<Object> items = (List<Object>)data.stream().flatMap(item-> {
                    Object r = item.getData(attribute);
                    if (r != null) {
                        if (r instanceof Collection) {
                            return ((Collection) r).stream();
                        } else if (r.toString().contains(DataSearcher.ARRAY_SEPARATOR)) {
                            return Stream.of(r.toString().split(DataSearcher.ARRAY_SEPARATOR));
                        } else {
                            return Stream.of(r);
                        }
                    }
                    return Stream.empty();
                }).filter(attr->attr!=null).collect(Collectors.toList());

                if(items.isEmpty()) return Collections.emptyList();

                return items.stream()
                        .collect(Collectors.groupingBy(t->t,Collectors.counting()))
                        .entrySet().stream().sorted((e1, e2)->e2.getValue().compareTo(e1.getValue()))
                        .map(e->{
                            Map<String,String> row = Collections.synchronizedMap(new HashMap<>());
                            row.put(attribute,e.getKey().toString());
                            row.put("count",e.getValue().toString());
                            return row;
                        }).collect(Collectors.toList());

            }
        };
        response.computeAttributesTask.fork();
        return response;
    }
}
