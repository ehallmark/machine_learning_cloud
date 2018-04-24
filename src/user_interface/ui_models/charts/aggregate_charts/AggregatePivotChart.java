package user_interface.ui_models.charts.aggregate_charts;

import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
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
    private static final String BUCKET_SUFFIX = "_b_";
    private static final String GROUP_SUFFIX = "_g_";
    protected Map<String,String> attrToCollectByAttrMap;
    protected Map<String,Type> attrToCollectTypeMap;
    protected Collection<AbstractAttribute> collectByAttributes;
    public AggregatePivotChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Collection<AbstractAttribute> collectByAttrs) {
        super(true,AGG_SUFFIX, attributes, groupByAttrs, Constants.GROUPED_FUNCTION_TABLE_CHART, false);
        this.collectByAttributes=collectByAttrs;
        this.attrToCollectByAttrMap=Collections.synchronizedMap(new HashMap<>());
        this.attrToCollectTypeMap=Collections.synchronizedMap(new HashMap<>());
    }

    private String getGroupSuffix() {
        return GROUP_SUFFIX+getAggSuffix();
    }
    private String getBucketSuffix() {
        return BUCKET_SUFFIX+getAggSuffix();
    }

    private String getBucketGroupSuffix() {
        return BUCKET_SUFFIX+GROUP_SUFFIX+getAggSuffix();
    }

    private String getAggSuffix() {
        return aggSuffix;
    }

    @Override
    public AggregatePivotChart dup() {
        return new AggregatePivotChart(attributes,groupByAttributes,collectByAttributes);
    }

    protected Function<String, ContainerTag> getCombineByTagFunction(Map<String, List<String>> groupedGroupAttrs) {
        return attrName -> {
            return div().withClass("row").with(
                    div().withClass("col-8").with(
                            label("Collect By"),br(),
                            SimilarPatentServer.technologySelectWithCustomClass(getCollectByAttrFieldName(attrName),getCollectByAttrFieldName(attrName),"single-select2",groupedGroupAttrs,"")
                    ),div().withClass("col-4").with(
                            label("Collecting Function"),br(),
                            select().withClass("single-select2").withName(getCollectTypeFieldName(attrName)).withId(getCollectTypeFieldName(attrName)).with(
                                    option("Count").withValue(""),
                                    option(Type.Sum.toString()).withValue(Type.Sum.toString()),
                                    option(Type.Average.toString()).withValue(Type.Average.toString()),
                                    option(Type.Max.toString()).withValue(Type.Max.toString()),
                                    option(Type.Min.toString()).withValue(Type.Min.toString()),
                                    option("Stats").withValue(Type.All.toString())
                            )
                    )
            );
        };
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        attrToCollectByAttrMap.clear();
        attrToCollectTypeMap.clear();
        super.extractRelevantInformationFromParams(params);
        if(this.attrNames!=null) {
            this.attrNames.forEach(attr -> {
                String collectByName = SimilarPatentServer.extractString(params, getCollectByAttrFieldName(attr), null);
                if(collectByName!=null) attrToCollectByAttrMap.put(attr,collectByName);
                String collectByType = SimilarPatentServer.extractString(params, getCollectTypeFieldName(attr), null);
                if(collectByType!=null) attrToCollectTypeMap.put(attr,Type.valueOf(collectByType));
            });
        }
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        List<AbstractAttribute> availableGroups = collectByAttributes.stream().filter(attr->attr.isDisplayable()&&userRoleFunction.apply(attr.getName())).collect(Collectors.toList());
        Map<String,List<String>> groupedGroupAttrs = new TreeMap<>(availableGroups.stream().collect(Collectors.groupingBy(filter->filter.getRootName())).entrySet()
                .stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(attr->attr.getFullName()).collect(Collectors.toList()))));
        Function<String,ContainerTag> additionalTagFunction = getCombineByTagFunction(groupedGroupAttrs);
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Arrays.asList(getCollectByAttrFieldName(attrName),getCollectTypeFieldName(attrName));
        return this.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,DEFAULT_COMBINE_BY_FUNCTION,groupByPerAttribute);
    }

    private static List<String> getCategoriesForAttribute(AbstractAttribute attribute) {
        List<String> dataSets; // custom category names
        if (attribute instanceof DatasetAttribute) {
            dataSets = ((DatasetAttribute) attribute).getCurrentDatasets().stream()
                    .map(e -> e.getFirst()).collect(Collectors.toList());
        } else if (attribute instanceof RangeAttribute) {
            dataSets = new ArrayList<>();
            RangeAttribute rangeAttribute = (RangeAttribute)attribute;
            // build categories
            double min = rangeAttribute.min().doubleValue();
            double max = rangeAttribute.max().doubleValue();
            int nBins = rangeAttribute.nBins();
            int step = (int) Math.round((max-min)/nBins);
            for(int j = 0; j < max; j += step) {
                dataSets.add(String.valueOf(j) + "-" + String.valueOf(j+step));
            }
        } else {
            dataSets = null;
        }
        return dataSets;
    }

    @Override
    public List<? extends TableResponse> create(AbstractAttribute attribute, Aggregations aggregations) {
        String attrName = attribute.getFullName();
        String aggName = getAggName(attrName);
        String bucketName = getBucketName(attrName);
        String groupedBucketName = getBucketGroupName(attrName);

        Type collectorType = attrToCollectTypeMap.get(attrName);
        String collectByAttrName = attrToCollectByAttrMap.get(attrName);

        List<String> dataSets = getCategoriesForAttribute(attribute);

       String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attrName);
        String humanSearchType = combineTypesToString(searchTypes);
        String yTitle = (collectByAttrName==null?humanSearchType:SimilarPatentServer.fullHumanAttributeFor(collectByAttrName)) + " "+ collectorType.toString() + " by "+ humanAttr;

        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        final boolean isGrouped = groupedByAttrName!=null;

        List<String> headers = new ArrayList<>();
        headers.add(attrName);
        Set<String> numericAttrNames = new HashSet<>();
        numericAttrNames.add(collectorType.toString());

        TableResponse response = new TableResponse();
        List<String> nonHumanAttrs = new ArrayList<>(0);
        List<String> groupByDatasets;
        List<Map<String,Object>> bucketData;

        if(isGrouped) { // handle two dimensional case (pivot)
            Aggregation bucketAgg = aggregations.get(groupedBucketName);
            bucketData = (List<Map<String, Object>>) bucketAgg.getMetaData().get("buckets");

            AbstractAttribute groupByAttribute = groupByAttributes.stream().filter(attr -> attr.getFullName().equals(groupedByAttrName)).limit(1).findFirst().orElse(null);
            if (groupByAttribute == null) {
                throw new RuntimeException("Unable to find collecting attribute: " + groupByAttribute.getFullName());
            }
            groupByDatasets = getCategoriesForAttribute(groupByAttribute);
            yTitle += " grouped by "+SimilarPatentServer.fullHumanAttributeFor(groupedByAttrName);
            for(int i = 0; i < bucketData.size(); i++) {
                // get groups
                Map<String,Object> bucket = bucketData.get(i);
                String key = bucket.getOrDefault("key_as_string", bucket.get("key")).toString();
                headers.add(key);
                numericAttrNames.add(key);
                nonHumanAttrs.add(key);
            }
        } else {
            String firstHeader = collectorType.toString();
            headers.add(firstHeader);
            numericAttrNames.add(firstHeader);
            Aggregation bucketAgg = aggregations.get(bucketName);
            bucketData = (List<Map<String, Object>>) bucketAgg.getMetaData().get("buckets");
            groupByDatasets = null;
        }

        response.type = getType();
        response.title = yTitle;
        response.headers = headers;
        response.numericAttrNames = numericAttrNames;
        response.nonHumanAttrs = nonHumanAttrs;

        System.out.println("Numeric Attrs: "+response.numericAttrNames.toString());

        if(isGrouped) {
            response.computeAttributesTask = new RecursiveTask<List<Map<String, String>>>() {
                @Override
                protected List<Map<String, String>> compute() {
                    List<Map<String, String>> data = new ArrayList<>();
                    Map<String,Map<String,Double>> groupedData = new HashMap<>();
                    List<String> allGroups = new ArrayList<>();
                    Set<String> allEntries = new HashSet<>();
                    for (int i = 0; i < bucketData.size(); i++) {
                        Map<String, Object> bucket = bucketData.get(i);
                        Object group = groupByDatasets == null ? bucket.getOrDefault("key_as_string", bucket.get("key")) : groupByDatasets.get(i);
                        if (group == null || group.toString().isEmpty()) group = "(empty)";
                        List<Map<String,Object>> nestedBucketData = (List<Map<String,Object>>) ((Map)bucket.get(bucketName)).get("buckets");
                        Map<String,Double> pairsByGroup = new HashMap<>();
                        for(int j = 0; j < nestedBucketData.size(); j++) {
                            Map<String, Object> nestedBucket = nestedBucketData.get(j);
                            Double val = (Double) ((Map<String, Object>) nestedBucket.get(aggName)).get("value");
                            Object label = dataSets == null ? nestedBucket.getOrDefault("key_as_string", bucket.get("key")) : dataSets.get(j);
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
                            Map<String, Double> groupData = groupedData.get(group);
                            point.put(group, String.valueOf(groupData.getOrDefault(entry,0d)));
                        });
                        data.add(point);
                    });
                    return data;
                }
            };
        } else {
            response.computeAttributesTask = new RecursiveTask<List<Map<String, String>>>() {
                @Override
                protected List<Map<String, String>> compute() {
                    List<Map<String, String>> data = new ArrayList<>();
                    for (int i = 0; i < bucketData.size(); i++) {
                        Map<String, Object> bucket = bucketData.get(i);
                        Object label = dataSets == null ? bucket.getOrDefault("key_as_string", bucket.get("key")) : dataSets.get(i);
                        if (label == null || label.toString().isEmpty()) label = "(empty)";
                        Double val = (Double) ((Map<String, Object>) bucket.get(aggName)).get("value");
                        Map<String, String> entry = new HashMap<>();
                        entry.put(collectorType.toString(), val.toString());
                        entry.put(attrName, label.toString());
                        data.add(entry);
                    }
                    return data;
                }
            };
        }
        response.computeAttributesTask.fork();
        return Collections.singletonList(response);
    }


    @Override
    public String getType() {
        return "pivot";
    }

    private String getBucketName(String attrName) {
        return attrName + getBucketSuffix();
    }

    private String getBucketGroupName(String attrName) {
        return attrName + getBucketGroupSuffix();
    }

    private String getGroupName(String attrName) {
        return attrName + getGroupSuffix();
    }

    private String getAggName(String attrName) {
        return attrName + getAggSuffix();
    }

    @Override
    public List<AbstractAggregation> getAggregations(AbstractAttribute attribute) {
        String attrName = attribute.getFullName();
        Type collectorType = attrToCollectTypeMap.get(attrName);
        String bucketSuffix = getBucketSuffix();
        BucketAggregation attrAgg = AggregatePieChart.buildDistributionAggregation(attribute, bucketSuffix);
        CombinedAggregation combinedAttrAgg = new CombinedAggregation(attrAgg, getAggName(attrName), collectorType);

        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        if(groupedByAttrName!=null) { // handle two dimensional case (pivot)
            AbstractAttribute groupByAttribute = groupByAttributes.stream().filter(attr->attr.getFullName().equals(groupedByAttrName)).limit(1).findFirst().orElse(null);
            if(groupByAttribute==null) {
                throw new RuntimeException("Unable to find collecting attribute: "+groupByAttribute.getFullName());
            }
            Integer groupLimit = attrNameToMaxGroupSizeMap.get(attrName);
            String groupBySuffix = getGroupSuffix();
            BucketAggregation groupAgg = AggregatePieChart.buildDistributionAggregation(groupByAttribute,groupBySuffix,groupLimit);
            AbstractAggregation twoDimensionalAgg = new AbstractAggregation() {
                @Override
                public AggregationBuilder getAggregation() {
                    return groupAgg.getAggregation().subAggregation(combinedAttrAgg.getAggregation());
                }
            };
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