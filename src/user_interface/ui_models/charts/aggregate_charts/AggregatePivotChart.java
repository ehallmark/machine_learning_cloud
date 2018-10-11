package user_interface.ui_models.charts.aggregate_charts;

import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.tables.TableResponse;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static j2html.TagCreator.*;

public class AggregatePivotChart extends AggregationChart<TableResponse> {
    private static final String AGG_SUFFIX = "_pivot";
    public AggregatePivotChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Collection<AbstractAttribute> collectByAttrs) {
        super(true,"Pivot Table",AGG_SUFFIX, attributes, groupByAttrs, collectByAttrs, Constants.PIVOT_FUNCTION_TABLE_CHART);
    }


    public static Tag getTable(TableResponse response, String type, int tableIdx) {
        List<String> nonHumanAttrs = response.nonHumanAttrs;
        List<String> humanHeaders = response.headers.stream().map(header->{
            if(nonHumanAttrs==null || !nonHumanAttrs.contains(header)) {
                return SimilarPatentServer.fullHumanAttributeFor(header);
            } else {
                return header;
            }
        }).collect(Collectors.toList());
        return div().attr("style", "width: 96%; margin-left: 2%; margin-bottom: 30px; overflow-x: auto;").withClass(type).withId("table-" + tableIdx).with(
                h5(response.title),br(),
                form().withMethod("post").withTarget("_blank").withAction(SimilarPatentServer.DOWNLOAD_URL).with(
                        input().withType("hidden").withName("tableId").withValue(String.valueOf(tableIdx)),
                        button("Download to CSV").withType("submit").withClass("btn btn-outline-secondary div-button").attr("style","width: 40%; margin-bottom: 20px;")
                ),
                table().withClass("table table-striped").withId(type+"-table-"+tableIdx+"table").attr("style","margin-left: 3%; margin-right: 3%; width: 94%;").with(
                        thead().with(
                                tr().with(
                                        IntStream.range(0,humanHeaders.size()).mapToObj(i->th(humanHeaders.get(i)).attr("data-dynatable-column", response.headers.get(i))).collect(Collectors.toList())
                                )
                        ), tbody(),
                        tfoot().with(
                                tr().with(
                                        td("Overall "+response.collectorType.toString())
                                ).with(
                                        humanHeaders.size()>0 ? IntStream.range(0, humanHeaders.size()-1).mapToObj(i->td().withId("table-"+tableIdx+"-totals-"+i))
                                                .collect(Collectors.toList())
                                                : Collections.emptyList()
                                )
                        )
                )
        )   ;
    }

    private ContainerTag getAdditionalTagPerAttr(String attrName) {
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        label("Max Rows").attr("title", "The maximum number of rows for this pivot chart (defaults to the maximum: 10,000).").attr("style","width: 100%;").with(
                                br(),
                                input().withId(getMaxSlicesField(attrName)).withName(getMaxSlicesField(attrName)).withType("number").withClass("form-control").withValue("")
                        )
                )
        );
    }


    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        Function<String,ContainerTag> additionalTagFunction = this::getAdditionalTagPerAttr;
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Collections.singletonList(getMaxSlicesField(attrName));
        Function2<ContainerTag,ContainerTag,ContainerTag> combineFunction = (tag1, tag2) -> div().withClass("row").with(
                div().withClass("col-10").with(
                        tag1
                ),div().withClass("col-2").with(
                        tag2
                )
        );
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,combineFunction,true,loadChildren,idToTagMap);
    }


    @Override
    public AggregatePivotChart dup() {
        return new AggregatePivotChart(attributes,groupByAttributes,collectByAttributes);
    }

    @Override
    public List<? extends TableResponse> create(Request req,  String attrName, AbstractAttribute attribute, AbstractAttribute groupByAttribute, AbstractAttribute collectByAttribute, Aggregations aggregations) {
        Type collectorType = attrToCollectTypeMap.get(attrName);
        String collectByAttrName = attrToCollectByAttrMap.get(attrName);

        String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attrName);
        String yTitle = (collectByAttrName==null?"Assets":SimilarPatentServer.fullHumanAttributeFor(collectByAttrName)) + " "+ collectorType + " by "+ humanAttr;

        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        final boolean isGrouped = groupedByAttrName!=null;
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        List<String> headers = new ArrayList<>();
        headers.add(attrName);
        Set<String> numericAttrNames = new HashSet<>();
        numericAttrNames.add(collectorType.toString());

        TableResponse response = new TableResponse();
        response.collectorType = collectorType;
        response.attribute=attribute;
        List<String> nonHumanAttrs = new ArrayList<>(0);
        List<String> groupByDatasets;
        Function<Aggregations, Number> subAggregationHandler = getSubAggregationHandler(attrName);

        if(isGrouped) { // handle two dimensional case (pivot)
            final String groupBySuffix = getGroupSuffix();
            final String groupAggName = getGroupByAttrName(attrName,groupedByAttrName,groupBySuffix);
            final String nestedGroupAggName = getGroupByAttrName(attrName,groupedByAttrName,NESTED_SUFFIX+groupBySuffix);
            if (groupByAttribute == null) {
                throw new RuntimeException("Unable to find group by attribute in pivot chart: " + groupedByAttrName);
            }
            response.groupByAttribute=groupByAttribute;
            final List<String> dataSets = getCategoriesForAttribute(attribute);
            Aggregation groupAgg = handlePotentiallyNestedAgg(aggregations,groupAggName,nestedGroupAggName);
            if(groupAgg==null) {
                System.out.println("Group agg: "+groupAggName);
                System.out.println("Available aggs: "+String.join(", ",aggregations.getAsMap().keySet()));
                throw new NullPointerException("Group agg is null");
            }
            groupByDatasets = getCategoriesForAttribute(groupByAttribute);
            if(groupAgg instanceof MultiBucketsAggregation) {
                MultiBucketsAggregation agg = (MultiBucketsAggregation)groupAgg;
                int i = 0;
                for(MultiBucketsAggregation.Bucket entry : agg.getBuckets()) {
                    String key = groupByDatasets==null?entry.getKeyAsString():groupByDatasets.get(i);
                    headers.add(key);
                    numericAttrNames.add(key);
                    nonHumanAttrs.add(key);
                    i++;
                }
            } else {
                throw new RuntimeException("Unable to cast group aggregation "+groupAggName.getClass().getName()+" to MultiBucketsAggregation.class");
            }
            yTitle += " grouped by "+SimilarPatentServer.fullHumanAttributeFor(groupedByAttrName);

            response.computeAttributesTask = new RecursiveTask<Pair<List<Map<String, String>>, List<Double>>>() {
                @Override
                protected Pair<List<Map<String, String>>, List<Double>> compute() {
                    List<Double> totals = new ArrayList<>();
                    List<Map<String, String>> data = new ArrayList<>();
                    Map<String,Map<String,Number>> groupedData = new HashMap<>();
                    List<String> allGroups = new ArrayList<>();
                    Set<String> allEntries = new HashSet<>();
                    MultiBucketsAggregation bucketAgg = (MultiBucketsAggregation)groupAgg;
                    for (int i = 0; i < bucketAgg.getBuckets().size(); i++) {
                        MultiBucketsAggregation.Bucket bucket = bucketAgg.getBuckets().get(i);
                        Object group = groupByDatasets == null ? bucket.getKeyAsString() : groupByDatasets.get(i);
                        if (group == null || group.toString().isEmpty()) group = "(empty)";
                        if(!includeBlank && group.equals("(empty)")) {
                            continue;
                        }
                        Aggregations nestedAggs = bucket.getAggregations();
                        List<Pair<String,Number>> nestedBucketData = extractValuesFromAggregation(nestedAggs,attribute,attrName,subAggregationHandler);
                        Map<String,Number> pairsByGroup = new HashMap<>();
                        for(int j = 0; j < nestedBucketData.size(); j++) {
                            Pair<String,Number> nestedBucket = nestedBucketData.get(j);
                            Number val = nestedBucket.getSecond();
                            Object label = dataSets == null ? nestedBucket.getFirst() : dataSets.get(j);
                            pairsByGroup.put(label.toString(),val);
                            allEntries.add(label.toString());
                        }
                        groupedData.put(group.toString(),pairsByGroup);
                        allGroups.add(group.toString());
                    }
                    // invert groups
                    allEntries.forEach(entry-> {
                        Map<String, String> point = new HashMap<>();
                        point.put(attrName,entry);
                        double total = 0L;
                        for(String group : allGroups) {
                            Map<String, Number> groupData = groupedData.get(group);
                            Number val = groupData.getOrDefault(entry,0);
                            point.put(group, val.toString());
                            switch(collectorType) {
                                case Count: {

                                }
                                case Cardinality: {

                                }
                                case Average: {
                                    // will need to divide by n at the end
                                }
                                case Sum: {
                                    total += val.doubleValue();
                                    break;
                                }
                                case Max: {
                                    total = Math.max(total, val.doubleValue());
                                    break;
                                }
                                case Min: {
                                    total = Math.min(total, val.doubleValue());
                                    break;
                                }
                                case StdDeviation: {

                                }
                                case Variance: {

                                }

                            }
                        }
                        if(collectorType.equals(Type.Average)) {
                            total /= allGroups.size();
                        }
                        totals.add(total);
                        data.add(point);
                    });
                    return new Pair<>(data, totals);
                }
            };

            response.type = getType();
            response.title = yTitle;
            response.headers = headers;
            response.numericAttrNames = numericAttrNames;
            response.nonHumanAttrs = nonHumanAttrs;

        } else {
            String firstHeader = collectorType.toString();
            headers.add(firstHeader);
            numericAttrNames.add(firstHeader);

            response.computeAttributesTask = new RecursiveTask<Pair<List<Map<String, String>>, List<Double>>>() {
                @Override
                protected Pair<List<Map<String, String>>, List<Double>> compute() {
                    List<Pair<String,Number>> bucketData = extractValuesFromAggregation(aggregations,attribute,attrName,subAggregationHandler);
                    List<Map<String, String>> data = new ArrayList<>();
                    double total = 0L;
                    for (int i = 0; i < bucketData.size(); i++) {
                        Pair<String,Number> bucket = bucketData.get(i);
                        String label = bucket.getFirst();
                        if (label == null || label.isEmpty()) label = "(empty)";
                        Map<String, String> entry = new HashMap<>();
                        Number val = bucket.getSecond();
                        switch(collectorType) {
                            case Count: {

                            }
                            case Cardinality: {

                            }
                            case Average: {
                                // will need to divide by n at the end
                            }
                            case Sum: {
                                total += val.doubleValue();
                                break;
                            }
                            case Max: {
                                total = Math.max(total, val.doubleValue());
                                break;
                            }
                            case Min: {
                                total = Math.min(total, val.doubleValue());
                                break;
                            }
                            case StdDeviation: {

                            }
                            case Variance: {

                            }

                        }
                        entry.put(collectorType.toString(), val.toString());
                        entry.put(attrName, label);
                        data.add(entry);
                    }
                    if(collectorType.equals(Type.Average)) {
                        total /= bucketData.size();
                    }
                    return new Pair<>(data, Collections.singletonList(total));
                }
            };

            response.type = getType();
            response.title = yTitle;
            response.headers = headers;
            response.numericAttrNames = numericAttrNames;
            response.nonHumanAttrs = nonHumanAttrs;

        }

        response.computeAttributesTask.fork();
        return Collections.singletonList(response);
    }


    @Override
    public String getType() {
        return "pivot";
    }

}
