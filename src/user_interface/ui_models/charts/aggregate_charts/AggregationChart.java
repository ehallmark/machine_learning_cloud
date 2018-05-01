package user_interface.ui_models.charts.aggregate_charts;

import lombok.Getter;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AggregationChart<T> extends AbstractChartAttribute {
    public static final int MAXIMUM_AGGREGATION_SIZE = 1000;
    public static final String NESTED_SUFFIX = "_n_";
    public static final String BUCKET_SUFFIX = "_b_";
    public static final String GROUP_SUFFIX = "_g_";
    protected final String aggSuffix;
    @Getter
    protected final boolean isTable;
    public AggregationChart(boolean isTable, String aggSuffix, Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, String name, boolean groupsPlottableOnSameChart) {
        super(attributes,groupByAttrs,name,true, groupsPlottableOnSameChart);
        this.aggSuffix=aggSuffix;
        this.isTable=isTable;
    }

    protected String removeChartNameFromAttrName(String attrName) {
        return attrName.substring(getName().replace("[]","").length()+1);
    }

    protected String getGroupSuffix() {
        return GROUP_SUFFIX+aggSuffix;
    }

    protected AbstractAggregation createGroupedAttribute(String groupedByAttrName, int groupLimit, AggregationBuilder innerAgg) {
        AbstractAttribute groupByAttribute = groupByAttributes.stream().filter(attr->attr.getFullName().equals(groupedByAttrName)).limit(1).findFirst().orElse(null);
        if(groupByAttribute==null) {
            throw new RuntimeException("Unable to find collecting attribute: "+groupByAttribute.getFullName());
        }
        String groupBySuffix = getGroupSuffix();
        BucketAggregation groupAgg = AggregatePieChart.buildDistributionAggregation(this,groupByAttribute,groupByAttribute.getFullName(),groupBySuffix,groupLimit);
        return new AbstractAggregation() {
            @Override
            public AggregationBuilder getAggregation() {
                return groupAgg.getAggregation().subAggregation(innerAgg);
            }
        };
    }

    public abstract List<? extends T> create(AbstractAttribute attribute, String attrName, Aggregations aggregations);

    public abstract AggregationChart<T> dup();

    public abstract String getType();

    public Aggregation handlePotentiallyNestedAgg(Aggregations aggregations, String attrName) {
        if(aggregations==null) return null; // important to stop recursion
        Aggregation agg = aggregations.get(attrName + aggSuffix);
        if(agg==null) {
            // try nested
            Nested nested = aggregations.get(attrName+NESTED_SUFFIX+aggSuffix);
            return handlePotentiallyNestedAgg(nested.getAggregations(),attrName);
        }
        return agg;
    }

    protected static List<String> getCategoriesForAttribute(AbstractAttribute attribute) {
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


    protected List<Pair<String,Number>> extractValuesFromAggregation(Aggregations aggregations, AbstractAttribute attribute, String attrName, Function<Aggregations,Number> subAggregationHandler) {
        Aggregation _agg = handlePotentiallyNestedAgg(aggregations,attrName);
        List<String> categories = getCategoriesForAttribute(attribute);
        List<Pair<String,Number>> bucketData = new ArrayList<>();
        if(_agg instanceof MultiBucketsAggregation) {
            MultiBucketsAggregation agg = (MultiBucketsAggregation)_agg;
            // For each entry
            int i = 0;
            for (MultiBucketsAggregation.Bucket entry : agg.getBuckets()) {
                String key = categories==null?entry.getKeyAsString():categories.get(i);          // bucket key
                long docCount = entry.getDocCount();            // Doc count
                if(subAggregationHandler==null) {
                    bucketData.add(new Pair<>(key, docCount));
                } else {
                    bucketData.add(new Pair<>(key,subAggregationHandler.apply(entry.getAggregations())));
                }
                i++;
            }
        } else {
            throw new RuntimeException("Unable to cast "+_agg.getClass().getName()+" to MultiBucketsAggregation.class");
        }
        return bucketData;
    }

    public static AbstractAttribute findAttribute(Collection<AbstractAttribute> nonNestedAttributes, String attrName) {
        return nonNestedAttributes.stream().filter(attr->attr.getFullName().equals(attrName)).limit(1).findFirst().orElse(null);
    }

    public List<AbstractAggregation> getAggregations(AbstractAttribute attribute, String attrName) {
        AbstractAggregation aggregation = AggregatePieChart.buildDistributionAggregation(this,attribute,attrName,aggSuffix);
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        if(groupedByAttrName!=null) { // handle two dimensional case (pivot)
            int groupLimit = attrNameToMaxGroupSizeMap.getOrDefault(attrName, MAXIMUM_AGGREGATION_SIZE);
            AbstractAggregation twoDimensionalAgg = createGroupedAttribute(groupedByAttrName,groupLimit,aggregation.getAggregation());
            return Collections.singletonList(
                    twoDimensionalAgg
            );
        } else {
            return Collections.singletonList(
                    aggregation
            );
        }
    }
}
