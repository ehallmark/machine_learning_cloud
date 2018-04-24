package user_interface.ui_models.charts.aggregate_charts;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;
import user_interface.ui_models.charts.aggregations.metrics.CombinedAggregation;
import user_interface.ui_models.charts.tables.TableResponse;

import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

public class AggregatePivot1DChart extends AggregationChart<TableResponse> {
    private static final String AGG_SUFFIX = "_p1d";
    protected Map<String,String> attrToCollectByAttrMap;
    protected Map<String,Type> attrToCollectTypeMap;
    protected Collection<AbstractAttribute> collectByAttributes;
    public AggregatePivot1DChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Collection<AbstractAttribute> collectByAttrs) {
        super(true,AGG_SUFFIX, attributes, groupByAttrs, Constants.GROUPED_FUNCTION_TABLE_CHART, false);
        this.collectByAttributes=collectByAttrs;
        this.attrToCollectByAttrMap=Collections.synchronizedMap(new HashMap<>());
        this.attrToCollectTypeMap=Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public AggregatePivot1DChart dup() {
        return new AggregatePivot1DChart(attributes,groupByAttributes,collectByAttributes);
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
    public List<? extends TableResponse> create(AbstractAttribute attribute, Aggregations aggregations) {
        String attrName = attribute.getFullName();
        String innerBucketName = attrName + aggSuffix;
        Type collectorType = attrToCollectTypeMap.get(attrName);
        String collectByAttrName = attrToCollectByAttrMap.get(attrName);

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

        Aggregation bucketAgg = aggregations.get(attrName + "bucket" + aggSuffix);
        List<Map<String, Object>> bucketData = (List<Map<String, Object>>) bucketAgg.getMetaData().get("buckets");
        String humanAttr = SimilarPatentServer.fullHumanAttributeFor(attrName);
        String humanSearchType = combineTypesToString(searchTypes);
        String yTitle = (collectByAttrName==null?humanSearchType:SimilarPatentServer.fullHumanAttributeFor(collectByAttrName)) + " "+ collectorType.toString() + " by "+ humanAttr;

        TableResponse response = new TableResponse();
        response.type = getType();
        response.title = yTitle;
        response.headers = new ArrayList<>();
        response.headers.add(attrName);
        response.headers.add(collectorType.toString());
        response.numericAttrNames = Collections.singleton(collectorType.toString());
        response.computeAttributesTask = new RecursiveTask<List<Map<String,String>>>() {
            @Override
            protected List<Map<String,String>> compute() {
                List<Map<String,String>> data = new ArrayList<>();
                for(int i = 0; i < bucketData.size(); i++) {
                    Map<String,Object> bucket = bucketData.get(i);
                    Object label = dataSets==null?bucket.getOrDefault("key_as_string", bucket.get("key")):dataSets.get(i);
                    if(label==null||label.toString().isEmpty()) label = "(empty)";
                    Double val = (Double) ((Map<String,Object>)bucket.get(innerBucketName)).get("value");
                    Map<String,String> entry = new HashMap<>();
                    entry.put(collectorType.toString(),val.toString());
                    entry.put(attrName,label.toString());
                }
                return data;
            }
        };
        response.computeAttributesTask.fork();
        return Collections.singletonList(response);
    }

    @Override
    public List<AbstractAggregation> getAggregations(AbstractAttribute attribute) {
        String attrName = attribute.getFullName();
        Type collectorType = attrToCollectTypeMap.get(attrName);
        String bucketSuffix = "bucket"+aggSuffix;
        BucketAggregation aggregation = AggregatePieChart.buildDistributionAggregation(attribute,bucketSuffix);
        return Collections.singletonList(
                new CombinedAggregation(aggregation,attrName + aggSuffix, collectorType)
        );
    }

}
