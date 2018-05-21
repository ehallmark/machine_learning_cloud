package user_interface.ui_models.charts.aggregate_charts;

import j2html.tags.Tag;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
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
        super(true,"Pivot Table",AGG_SUFFIX, attributes, groupByAttrs, collectByAttrs, Constants.PIVOT_FUNCTION_TABLE_CHART, false);
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
                        button("Download to Excel").withType("submit").withClass("btn btn-outline-secondary div-button").attr("style","width: 40%; margin-bottom: 20px;")
                ),
                table().withClass("table table-striped").withId(type+"-table-"+tableIdx+"table").attr("style","margin-left: 3%; margin-right: 3%; width: 94%;").with(
                        thead().with(
                                tr().with(
                                        IntStream.range(0,humanHeaders.size()).mapToObj(i->th(humanHeaders.get(i)).attr("data-dynatable-column", response.headers.get(i))).collect(Collectors.toList())
                                )
                        ), tbody()
                )
        )   ;
    }


    @Override
    public AggregatePivotChart dup() {
        return new AggregatePivotChart(attributes,groupByAttributes,collectByAttributes);
    }

    @Override
    public List<? extends TableResponse> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        Type collectorType = attrToCollectTypeMap.get(attrName);
        String collectByAttrName = attrToCollectByAttrMap.get(attrName);

        String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attrName);
        String humanSearchType = combineTypesToString(searchTypes);
        String yTitle = (collectByAttrName==null?humanSearchType:SimilarPatentServer.fullHumanAttributeFor(collectByAttrName)) + " "+ collectorType + " by "+ humanAttr;

        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        final boolean isGrouped = groupedByAttrName!=null;
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        List<String> headers = new ArrayList<>();
        headers.add(attrName);
        Set<String> numericAttrNames = new HashSet<>();
        numericAttrNames.add(collectorType.toString());

        TableResponse response = new TableResponse();
        List<String> nonHumanAttrs = new ArrayList<>(0);
        List<String> groupByDatasets;
        Function<Aggregations, Number> subAggregationHandler = getSubAggregationHandler(attrName);

        if(isGrouped) { // handle two dimensional case (pivot)
            AbstractAttribute groupByAttribute = findAttribute(groupByAttributes,groupedByAttrName);
            final String groupBySuffix = getGroupSuffix();
            final String groupAggName = getGroupByAttrName(attrName,groupedByAttrName,groupBySuffix);
            final String nestedGroupAggName = getGroupByAttrName(attrName,groupedByAttrName,NESTED_SUFFIX+groupBySuffix);
            if (groupByAttribute == null) {
                throw new RuntimeException("Unable to find group by attribute in pivot chart: " + groupedByAttrName);
            }
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

            response.computeAttributesTask = new RecursiveTask<List<Map<String, String>>>() {
                @Override
                protected List<Map<String, String>> compute() {
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
                        allGroups.forEach(group -> {
                            Map<String, Number> groupData = groupedData.get(group);
                            point.put(group, groupData.getOrDefault(entry,0).toString());
                        });
                        data.add(point);
                    });
                    return data;
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

            response.computeAttributesTask = new RecursiveTask<List<Map<String, String>>>() {
                @Override
                protected List<Map<String, String>> compute() {
                    List<Pair<String,Number>> bucketData = extractValuesFromAggregation(aggregations,attribute,attrName,subAggregationHandler);
                    List<Map<String, String>> data = new ArrayList<>();
                    for (int i = 0; i < bucketData.size(); i++) {
                        Pair<String,Number> bucket = bucketData.get(i);
                        String label = bucket.getFirst();
                        if (label == null || label.isEmpty()) label = "(empty)";
                        Map<String, String> entry = new HashMap<>();
                        entry.put(collectorType.toString(), bucket.getSecond().toString());
                        entry.put(attrName, label);
                        data.add(entry);
                    }
                    return data;
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
