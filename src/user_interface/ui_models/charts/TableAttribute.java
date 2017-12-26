package user_interface.ui_models.charts;

import data_pipeline.helpers.Function2;
import elasticsearch.DataSearcher;
import j2html.tags.Tag;
import lombok.Getter;
import model.nodes.FactorNode;
import org.nd4j.linalg.primitives.Pair;
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
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class TableAttribute extends AbstractChartAttribute {
    enum CollectorType {
        Count, Max, Min, Sum, Average
    }

    @Getter
    protected String collectByAttrName;
    protected GroupedCountTableChart.CollectorType collectorType;
    protected final Collection<AbstractAttribute> collectByAttrs;
    private final CollectorType defaultCollectType;
    public TableAttribute(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupedByAttributes, Collection<AbstractAttribute> collectByAttrs, CollectorType defaultCollectType, String name) {
        super(attributes,groupedByAttributes,name,false, false);
        this.collectByAttrs=collectByAttrs;
        this.defaultCollectType=defaultCollectType;
    }


    public List<TableResponse> createTables(PortfolioList portfolioList) {
        if(attrNames==null||attrNames.isEmpty()) return Collections.emptyList();
        System.out.println("Table attr list: "+String.join("; ",attrNames));
        return Stream.of(attrNames).flatMap(attrList->{
            List<String> humanAttrs =  attrList.stream().map(attribute->SimilarPatentServer.fullHumanAttributeFor(attribute)).collect(Collectors.toList());
            String humanSearchType = combineTypesToString(searchTypes);
            String title = (collectByAttrName==null?humanSearchType:SimilarPatentServer.fullHumanAttributeFor(collectByAttrName)) + " "+ collectorType.toString() + (humanAttrs.isEmpty() ? "" :  " by "+ (humanAttrs.isEmpty() ? "*BLANK*" : String.join(", ",humanAttrs)));
            return groupPortfolioListForGivenAttribute(portfolioList,"").map(groupPair-> {
                return createHelper(groupPair.getSecond().getItemList(), attrList, title, groupPair.getFirst());
            });
        }).collect(Collectors.toList());
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        collectByAttrName = SimilarPatentServer.extractString(params,getCollectByAttrFieldName(null), null);
        collectorType = CollectorType.valueOf(SimilarPatentServer.extractString(params,getCollectTypeFieldName(null), defaultCollectType.toString()));
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        List<AbstractAttribute> availableGroups = collectByAttrs.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));

        Function2<Tag,Tag,Tag> combineTagFunction = (tag1, tag2) -> div().with(tag1,tag2);
        Function<String,Tag> additionalTagFunction = getCombineByTagFunction(groupedGroupAttrs);
        return this.getOptionsTag(userRoleFunction,additionalTagFunction,null,combineTagFunction,groupByPerAttribute);
    }


    @Override
    public List<String> getInputIds() {
        return Arrays.asList(
                getGroupByChartFieldName(null),
                getId(),
                getGroupByChartFieldName("")+MAX_GROUP_FIELD,
                getGroupByChartFieldName("")+INCLUDE_BLANK_FIELD,
                getCollectByAttrFieldName(null),
                getCollectTypeFieldName(null)
        );
    }

    protected abstract Function<String,Tag> getCombineByTagFunction(Map<String,List<String>> groupedGroupAttrs);

    protected String getCollectByAttrFieldName(String attrName) {
        return (getName().replace("[","").replace("]","")+SimilarPatentServer.COLLECT_BY_ATTR_FIELD+(attrName==null?"":attrName)).replace(".","");
    }

    protected String getCollectTypeFieldName(String attrName) {
        return (getName().replace("[","").replace("]","")+SimilarPatentServer.COLLECT_TYPE_FIELD+(attrName==null?"":attrName)).replace(".","");
    }

    public static Tag getTable(TableResponse response, String type, int tableIdx) {
        return div().attr("style", "width: 80%; margin-left: 10%; margin-bottom: 30px;").withClass(type).withId("table-" + tableIdx).with(
                h5(response.title),br(),
                form().withMethod("post").withTarget("_blank").withAction(SimilarPatentServer.DOWNLOAD_URL).with(
                        input().withType("hidden").withName("tableId").withValue(String.valueOf(tableIdx)),
                        button("Download to Excel").withType("submit").withClass("btn btn-secondary div-button").attr("style","width: 40%; margin-bottom: 20px;")
                ),
                table().withClass("table table-striped").withId(type+"-table-"+tableIdx+"table").attr("style","margin-left: 3%; margin-right: 3%; width: 94%;").with(
                        thead().with(
                                tr().with(
                                        response.headers.stream().map(header -> th(SimilarPatentServer.fullHumanAttributeFor(header)).attr("data-dynatable-column", header)).collect(Collectors.toList())
                                )
                        ), tbody()
                )
        )   ;
    }

    private TableResponse createHelper(List<Item> data, List<String> attrList, String yTitle, String subTitle) {
        TableResponse response = new TableResponse();
        response.type=getType();
        response.title=yTitle + (subTitle!=null&&subTitle.length()>0 ? (" (Grouped by "+subTitle+")") : "");
        response.headers = new ArrayList<>();
        response.headers.addAll(attrList);
        response.headers.add(collectorType.toString());
        response.numericAttrNames = Collections.singleton(collectorType.toString());
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
                Collector<Pair<Item,DeepList<Object>>,?,? extends Number> collector = getCollectorFromCollectorType();

                return items.stream()
                        .collect(Collectors.groupingBy(t->t.getSecond(),collector))
                        .entrySet().stream().sorted((e1, e2)->Double.compare(e2.getValue().doubleValue(),e1.getValue().doubleValue()))
                        .map(e->{
                            Map<String,String> row = Collections.synchronizedMap(new HashMap<>());
                            for(int i = 0; i < attrList.size(); i++) {
                                if(i>=e.getKey().size()) System.out.println("WARNING 2: "+e.getKey()+"  ->  "+attrList);
                                row.put(attrList.get(i),e.getKey().get(i).toString());
                            }
                            row.put(collectorType.toString(),e.getValue()==null?"":e.getValue().toString());
                            return row;
                        }).collect(Collectors.toList());

            }
        };
        response.computeAttributesTask.fork();
        return response;
    }

    Collector<Pair<Item,DeepList<Object>>,?,? extends Number> getCollectorFromCollectorType() {
        ToDoubleFunction<? super Pair<Item,DeepList<Object>>> toDoubleFunction = pair -> collectByAttrName == null ? 0 : Double.valueOf(pair.getFirst().getDataMap().getOrDefault(collectByAttrName,"0").toString());
        ToLongFunction<? super Pair<Item,DeepList<Object>>> toLongFunction = pair -> collectByAttrName == null ? 1L : pair.getFirst().getDataMap().getOrDefault(collectByAttrName,"").toString().split(DataSearcher.ARRAY_SEPARATOR).length;
        Collector<Pair<Item,DeepList<Object>>,?,Double> doubleCollector = null;
        Collector<Pair<Item,DeepList<Object>>,?,Long> longCollector = null;
        switch (collectorType) {
            case Average: {
                doubleCollector = Collectors.averagingDouble(toDoubleFunction);
                break;
            }
            case Sum: {
                doubleCollector = Collectors.summingDouble(toDoubleFunction);
                break;
            }
            case Count: {
                longCollector = Collectors.summingLong(toLongFunction);
                break;
            }
            case Max: {
                doubleCollector = Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparing(p->toDoubleFunction.applyAsDouble(p))),option->{
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
                doubleCollector = Collectors.collectingAndThen(Collectors.minBy(Comparator.comparing(p->toDoubleFunction.applyAsDouble(p))),option->{
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
                throw new RuntimeException("Unknown collect by type: "+collectorType);
            }
        }
        if(doubleCollector!=null) return doubleCollector;
        else return longCollector;
    }
}
