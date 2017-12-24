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
import java.util.function.DoubleFunction;
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
public class GroupedTableChart extends TableAttribute {
    enum CollectorType {
        Count, Max, Min, Sum, Average
    }
    private String collectByAttrName;
    private CollectorType collectorType;
    private Collection<AbstractAttribute> numericAttrs;
    public GroupedTableChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttrs,  Collection<AbstractAttribute> numericAttrs) {
        super(attributes, groupedByAttrs, Constants.GROUPED_TABLE_CHART);
        this.numericAttrs=numericAttrs;
    }

    @Override
    public List<TableResponse> createTables(PortfolioList portfolioList) {
        return Stream.of(attrNames).flatMap(attrList->{
            List<String> humanAttrs =  attrList.stream().map(attribute->SimilarPatentServer.fullHumanAttributeFor(attribute)).collect(Collectors.toList());
            String humanSearchType = combineTypesToString(searchTypes);
            String title = humanSearchType + collectorType.toString() + " by "+String.join(", ",humanAttrs);
            return groupPortfolioListForGivenAttribute(portfolioList,"").map(groupPair-> {
                return createHelper(groupPair.getSecond().getItemList(), attrList, title, groupPair.getFirst());
            });
        }).collect(Collectors.toList());
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);

        collectByAttrName = SimilarPatentServer.extractString(params,getCollectByAttrFieldName(null), null);
        collectorType = CollectorType.valueOf(SimilarPatentServer.extractString(params,getCollectTypeFieldName(null),CollectorType.Count.toString()));

        if(!collectorType.equals(CollectorType.Count) && collectByAttrName==null) {
            throw new UnsupportedOperationException("Must select an attribute to collect unless collecting by Count. [Method chosen: "+collectorType.toString()+"]");
        }
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        List<AbstractAttribute> availableGroups = numericAttrs.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));

        Function2<Tag,Tag,Tag> combineTagFunction = (tag1, tag2) -> div().with(tag1,tag2);
        Function<String,Tag> additionalTagFunction = attrName -> {
            return div().withClass("row").with(
                    div().withClass("col-9").with(
                            label("Collect By"),br(),
                            SimilarPatentServer.technologySelectWithCustomClass(getCollectByAttrFieldName(null),getCollectByAttrFieldName(null),"single-select2",groupedGroupAttrs,"Assets (default)")
                    ),div().withClass("col-3").with(
                            label("Collecting Function"),br(),
                            select().withClass("single-select2").withName(getCollectTypeFieldName(null)).withId(getCollectTypeFieldName(null)).with(
                                    option(CollectorType.Count.toString()).attr("selected","selected").withValue(""),
                                    option(CollectorType.Sum.toString()).withValue(CollectorType.Sum.toString()),
                                    option(CollectorType.Average.toString()).withValue(CollectorType.Average.toString()),
                                    option(CollectorType.Max.toString()).withValue(CollectorType.Max.toString()),
                                    option(CollectorType.Min.toString()).withValue(CollectorType.Min.toString())
                            )
                    )
            );
        };
        return this.getOptionsTag(userRoleFunction,additionalTagFunction,null,combineTagFunction,groupByPerAttribute);
    }

    @Override
    public TableAttribute dup() {
        return new GroupedTableChart(attributes, groupByAttributes,numericAttrs);
    }

    @Override
    public String getType() {
        return "groupedTable";
    }

    @Override
    public List<String> getInputIds() {
        List<String> prev = super.getInputIds();
        if(prev==null) prev = new ArrayList<>();
        else prev = new ArrayList<>(prev);
        prev.add(getGroupByChartFieldName("")+MAX_GROUP_FIELD);
        prev.add(getCollectByAttrFieldName(null));
        prev.add(getCollectTypeFieldName(null));
        return prev;
    }

    protected String getCollectByAttrFieldName(String attrName) {
        return (getName().replace("[","").replace("]","")+SimilarPatentServer.COLLECT_BY_ATTR_FIELD+(attrName==null?"":attrName)).replace(".","");
    }

    protected String getCollectTypeFieldName(String attrName) {
        return (getName().replace("[","").replace("]","")+SimilarPatentServer.COLLECT_TYPE_FIELD+(attrName==null?"":attrName)).replace(".","");
    }

    private TableResponse createHelper(List<Item> data, List<String> attrList, String yTitle, String subTitle) {
        TableResponse response = new TableResponse();
        response.type=getType();
        response.title=yTitle + (subTitle!=null&&subTitle.length()>0 ? (" (Grouped by "+subTitle+")") : "");
        response.headers = new ArrayList<>();
        response.headers.addAll(attrList);
        response.headers.add("Count");
        response.numericAttrNames = Collections.synchronizedSet(new HashSet<>(Collections.singletonList("Count")));
        response.computeAttributesTask = new RecursiveTask<List<Map<String,String>>>() {
            @Override
            protected List<Map<String,String>> compute() {
                if(data.size()==0) return Collections.emptyList();
                String[] attrsArray = attrList.toArray(new String[]{});
                List<Pair<Item,DeepList<Object>>> items = (List<Pair<Item,DeepList<Object>>>)data.stream().flatMap(item-> {
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
                        return new Pair<>(item,
                                new DeepList<>(
                                        IntStream.range(0,assignment.length).mapToObj(i->{
                                            if(i>=rs.size()) System.out.println("WARNING 1: "+factor.toString());
                                            List<?> r = rs.get(i);
                                            return r.size()>0?r.get(assignment[i]):"";
                                        }).collect(Collectors.toList())
                                )
                        );
                    });
                }).collect(Collectors.toList());

                if(items.isEmpty()) return Collections.emptyList();

                System.out.println("Starting to group table...");
                Collector<Pair<Item,DeepList<Object>>,?,Double> collector;
                ToDoubleFunction<? super Pair<Item,DeepList<Object>>> toDoubleFunction = pair -> collectByAttrName == null ? 0 : Double.valueOf(pair.getFirst().getDataMap().getOrDefault(collectByAttrName,"0").toString());
                if(collectorType==null) collectorType = CollectorType.Count;
                switch (collectorType) {
                    case Average: {
                        collector = Collectors.averagingDouble(toDoubleFunction);
                        break;
                    }
                    case Sum: {
                        collector = Collectors.summingDouble(toDoubleFunction);
                        break;
                    }
                    case Count: {
                        collector = Collectors.collectingAndThen(Collectors.counting(),n->n.doubleValue());
                        break;
                    }
                    case Max: {
                        collector = Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparing(p->toDoubleFunction.applyAsDouble(p))),option->{
                            Pair<Item,DeepList<Object>> pair = option.orElse(null);
                            if(pair == null) {
                                return null;
                            } else {
                                return toDoubleFunction.applyAsDouble(pair);
                            }
                        });
                        break;
                    }
                    case Min: {
                        collector = Collectors.collectingAndThen(Collectors.minBy(Comparator.comparing(p->toDoubleFunction.applyAsDouble(p))),option->{
                            Pair<Item,DeepList<Object>> pair = option.orElse(null);
                            if(pair == null) {
                                return null;
                            } else {
                                return toDoubleFunction.applyAsDouble(pair);
                            }
                        });
                        break;
                    }
                    default: {
                        collector = Collectors.collectingAndThen(Collectors.counting(),n->n.doubleValue());
                        break;
                    }
                }
                return items.stream()
                        .collect(Collectors.groupingBy(t->t.getSecond(),collector))
                        .entrySet().stream().sorted((e1, e2)->Double.compare(e2.getValue().doubleValue(),e1.getValue().doubleValue()))
                        .map(e->{
                            Map<String,String> row = Collections.synchronizedMap(new HashMap<>());
                            for(int i = 0; i < attrList.size(); i++) {
                                if(i>=e.getKey().size()) System.out.println("WARNING 2: "+e.getKey()+"  ->  "+attrList);
                                row.put(attrList.get(i),e.getKey().get(i).toString());
                            }
                            row.put("Count",e.getValue()==null?"":e.getValue().toString());
                            return row;
                        }).collect(Collectors.toList());

            }
        };
        response.computeAttributesTask.fork();
        return response;
    }
}
