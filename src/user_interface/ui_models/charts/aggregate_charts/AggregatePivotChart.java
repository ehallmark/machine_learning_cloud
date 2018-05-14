package user_interface.ui_models.charts.aggregate_charts;

import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.Setter;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.min.Min;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStats;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.google.elasticsearch.attributes.SimilarityAttribute;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;
import user_interface.ui_models.charts.aggregations.metrics.CombinedAggregation;
import user_interface.ui_models.charts.tables.TableResponse;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public class AggregatePivotChart extends AggregationChart<TableResponse> {
    private static final String AGG_SUFFIX = "_pivot";
    protected Collection<AbstractAttribute> collectByAttributes;
    @Setter
    private Map<String,SimilarityAttribute> similarityModels;
    public AggregatePivotChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Collection<AbstractAttribute> collectByAttrs) {
        super(true,"Pivot Table",AGG_SUFFIX, attributes, groupByAttrs, Constants.PIVOT_FUNCTION_TABLE_CHART, false);
        this.collectByAttributes=collectByAttrs;
    }



    @Override
    public AggregatePivotChart dup() {
        return new AggregatePivotChart(attributes,groupByAttributes,collectByAttributes);
    }

    protected Function<String, ContainerTag> getCombineByTagFunction(Map<String, List<String>> groupedGroupAttrs) {
        return attrName -> {
            return div().withClass("row collect-container").with(
                    div().withClass("col-8").with(
                            label("Collect By"),br(),
                            select().attr("style","width:100%;").withName(getCollectByAttrFieldName(attrName)).withId(getCollectByAttrFieldName(attrName)).withClass("collect-by-select")
                                    .with(option("").withValue(""))
                                    .with(
                                            groupedGroupAttrs.entrySet().stream().map(e-> {
                                                String optGroup = e.getKey();
                                                return optgroup().attr("label",SimilarPatentServer.humanAttributeFor(optGroup)).attr("name",optGroup).with(
                                                        e.getValue().stream().map(technology->{
                                                            return div().with(option(SimilarPatentServer.humanAttributeFor(technology)).withValue(technology));
                                                        }).collect(Collectors.toList())
                                                );
                                            }).collect(Collectors.toList())
                                    )
                    ),div().withClass("col-4").with(
                            label("Collecting Function"),br(),
                            select().withClass("single-select2 collect-type").withName(getCollectTypeFieldName(attrName)).withId(getCollectTypeFieldName(attrName))
                    )
            );
        };
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(this.attrNames!=null) {
            this.attrNames.forEach(attr -> {
                System.out.println("Looking for field: "+attr+" -> "+getCollectByAttrFieldName(attr));
                String collectByName = SimilarPatentServer.extractString(params, getCollectByAttrFieldName(attr), null);
                if(collectByName!=null) attrToCollectByAttrMap.put(attr,collectByName);
                String collectByType = SimilarPatentServer.extractString(params, getCollectTypeFieldName(attr), Type.Count.toString());
                if(collectByType==null) collectByType = Type.Count.toString();
                System.out.println("Found type: "+collectByType);
                attrToCollectTypeMap.put(attr,Type.valueOf(collectByType));
            });
        }
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        List<AbstractAttribute> availableGroups = collectByAttributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));
        Function<String,ContainerTag> additionalTagFunction = getCombineByTagFunction(groupedGroupAttrs);
        Function<String,List<String>> additionalInputIdsFunction = attrName -> {
            List<String> ids = Arrays.asList(getCollectByAttrFieldName(attrName),getCollectTypeFieldName(attrName));
            return ids;
        };
        return this.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,DEFAULT_COMBINE_BY_FUNCTION,groupByPerAttribute);
    }

    @Override
    public List<? extends TableResponse> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        final String statsAggName = getStatsAggName(attrName);
        final String nestedStatsAggName = getStatsAggName(attrName+NESTED_SUFFIX);
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

        Function<Aggregations,Number> subAggregationHandler = collectByAttrName == null ? null : subAggs -> {
            final Aggregation sub = handlePotentiallyNestedAgg(subAggs,statsAggName,nestedStatsAggName);
            Number val;
            switch (collectorType) {
                case Max: {
                    val= ((Max)sub).getValue();
                    break;
                }
                case Min: {
                    val= ((Min)sub).getValue();
                    break;
                }
                case Sum: {
                    val= ((Sum)sub).getValue();
                    break;
                }
                case Average: {
                    val= ((Avg)sub).getValue();
                    break;
                }
                case Cardinality: {
                    val= ((Cardinality)sub).getValue();
                    break;
                }
                case Count: {
                    val= ((ValueCount)sub).getValue();
                    break;
                }
                case Variance: {
                    val= ((ExtendedStats)sub).getVariance();
                    break;
                }
                case StdDeviation: {
                    val= ((ExtendedStats)sub).getStdDeviation();
                    break;
                }
                default: {
                    val = null;
                    break;
                }
            }
            return val;
        };

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

    private String getStatsSuffix() {
        return BUCKET_SUFFIX + aggSuffix;
    }

    private String getStatsAggName(String attrName) {
        return attrName + getStatsSuffix();
    }

    @Override
    public List<AbstractAggregation> getAggregations(Request req, AbstractAttribute attribute, String attrName) {
        Type collectorType = attrToCollectTypeMap.get(attrName);
        String collectByAttrName = attrToCollectByAttrMap.get(attrName);
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        if(collectorType==null && collectByAttrName!=null) throw new RuntimeException("Please select collector type.");
        if(collectByAttrName==null && collectorType!=null && !collectorType.equals(Type.Count)) throw new RuntimeException("Please select collect by attribute name.");

        System.out.println("Collecting by attribute: "+collectByAttrName);
        System.out.println("Collect by: "+collectorType);
        System.out.println("Available collector attrs: "+String.join("; ",attrToCollectByAttrMap.keySet()));

        AbstractAttribute collectByAttribute = collectByAttrName==null?null:findAttribute(collectByAttributes,collectByAttrName);
        if(collectByAttribute==null) {
            System.out.println("Collect by attribute could not be found: "+collectByAttrName);
        }
        if(collectByAttribute!=null && collectByAttribute instanceof SimilarityAttribute) {
            // need to get the original model for the vectors
            collectByAttribute=similarityModels.get(collectByAttribute.getName());
        } else if(collectByAttribute!=null && collectByAttribute instanceof DependentAttribute) {
            ((DependentAttribute)collectByAttribute).extractRelevantInformationFromParams(req);
        }
        BucketAggregation attrAgg = AggregatePieChart.buildDistributionAggregation(this,attribute, attrName,null, aggSuffix, MAXIMUM_AGGREGATION_SIZE, includeBlank, null);
        CombinedAggregation combinedAttrAgg = new CombinedAggregation(attrAgg, getStatsAggName(attrName), getStatsAggName(attrName+NESTED_SUFFIX), collectByAttribute, collectorType);
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        if(groupedByAttrName!=null) { // handle two dimensional case (pivot)
            int groupLimit = attrNameToMaxGroupSizeMap.getOrDefault(attrName, AggregatePieChart.DEFAULT_MAX_SLICES);
            AbstractAggregation twoDimensionalAgg = createGroupedAttribute(req, attrName,groupedByAttrName,groupLimit,combinedAttrAgg.getAggregation(), includeBlank);
            return Collections.singletonList(
                    twoDimensionalAgg
            );
        } else {
            return Collections.singletonList(
                    combinedAttrAgg
            );
        }
    }


}
