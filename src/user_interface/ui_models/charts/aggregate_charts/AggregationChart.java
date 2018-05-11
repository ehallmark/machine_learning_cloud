package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.drilldown.DrilldownPoint;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
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
    protected String chartTitle;
    public AggregationChart(boolean isTable, String chartTitle, String aggSuffix, Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, String name, boolean groupsPlottableOnSameChart) {
        super(attributes,groupByAttrs,name,true, groupsPlottableOnSameChart);
        this.aggSuffix=aggSuffix;
        this.isTable=isTable;
        this.chartTitle=chartTitle;
    }

    protected String removeChartNameFromAttrName(String attrName) {
        return attrName.substring(getName().replace("[]","").length()+1);
    }

    protected String getGroupSuffix() {
        return GROUP_SUFFIX+aggSuffix;
    }

    public String getGroupByAttrName(String attrName, String groupByAttr, String suffix) {
        return attrName + groupByAttr + suffix;
    }

    protected List<Series<?>> createDataForAggregationChart(Options options, Aggregations aggregations, AbstractAttribute attribute, String attrName, String title, Integer limit, boolean drilldown) {
        List<Series<?>> data = new ArrayList<>();
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        final boolean isGrouped = groupedByAttrName!=null;
        System.out.println("Group by attrname for "+attrName+": "+groupedByAttrName);
        System.out.println("Available keys: "+String.join("; ", attrNameToGroupByAttrNameMap.keySet()));
        if(isGrouped) {
            PointSeries drilldownSeries = new PointSeries();
            AbstractAttribute groupByAttribute = findAttribute(groupByAttributes,groupedByAttrName);
            final String groupAggName = getGroupByAttrName(attrName,groupedByAttrName,getGroupSuffix());
            if (groupByAttribute == null) {
                throw new RuntimeException("Unable to find collecting attribute: " + groupByAttribute.getFullName());
            }
            Aggregation groupAgg = aggregations.get(groupAggName);
            if(groupAgg==null) {
                System.out.println("Group agg: "+groupAggName);
                System.out.println("Available aggs: "+String.join(", ",aggregations.getAsMap().keySet()));
                throw new NullPointerException("Group agg is null");
            }
            List<String> groupByDatasets = getCategoriesForAttribute(groupByAttribute);
            if(groupAgg instanceof MultiBucketsAggregation) {
                MultiBucketsAggregation agg = (MultiBucketsAggregation)groupAgg;
                int i = 0;
                for(MultiBucketsAggregation.Bucket entry : agg.getBuckets()) {
                    String group = groupByDatasets==null?entry.getKeyAsString():groupByDatasets.get(i);
                    Aggregations nestedAggs = entry.getAggregations();
                    PointSeries series = getSeriesFromAgg(nestedAggs,attribute,attrName,group,limit);
                    if(drilldown) {
                        drilldownSeries.addPoint(createPoint(group,entry.getDocCount(),series,options));
                    } else {
                        data.add(series);
                    }
                    i++;
                }
            } else {
                throw new RuntimeException("Unable to cast group aggregation "+groupAggName.getClass().getName()+" to MultiBucketsAggregation.class");
            }
            if(drilldown) {
                data.add(drilldownSeries);
            }
        } else {
            PointSeries series = getSeriesFromAgg(aggregations, attribute, attrName, title, limit);
            data.add(series);
        }
        return data;
    }

    protected AbstractAggregation createGroupedAttribute(String attrName, String groupedByAttrName, int groupLimit, AggregationBuilder innerAgg) {
        AbstractAttribute groupByAttribute = findAttribute(groupByAttributes,groupedByAttrName);
        if(groupByAttribute==null) {
            throw new RuntimeException("Unable to find grouping attribute attribute: "+groupedByAttrName);
        }
        String groupBySuffix = getGroupSuffix();
        BucketAggregation groupAgg = AggregatePieChart.buildDistributionAggregation(this,groupByAttribute,groupByAttribute.getFullName(),attrName,groupBySuffix,groupLimit);
        return new AbstractAggregation() {
            @Override
            public AggregationBuilder getAggregation() {
                return groupAgg.getAggregation().subAggregation(innerAgg);
            }
        };
    }

    protected PointSeries getSeriesFromAgg(Aggregations aggregations, AbstractAttribute attribute, String attrName, String title, Integer limit) {
        PointSeries series = new PointSeries();
        series.setName(title);
        final List<String> dataSets = getCategoriesForAttribute(attribute);
        List<Pair<String,Long>> bucketData = new ArrayList<>();
        MultiBucketsAggregation agg = (MultiBucketsAggregation)handlePotentiallyNestedAgg(aggregations,attrName);
        // For each entry
        {
            int i = 0;
            for (MultiBucketsAggregation.Bucket entry : agg.getBuckets()) {
                String key = dataSets == null ? entry.getKeyAsString() : dataSets.get(i);          // bucket key
                long docCount = entry.getDocCount();            // Doc count
                bucketData.add(new Pair<>(key, docCount));
                i++;
            }
        }
        double remaining = 0d;
        for(int i = 0; i < bucketData.size(); i++) {
            Pair<String,Long> bucket = bucketData.get(i);
            Object label = bucket.getFirst();
            if(label==null||label.toString().isEmpty()) label = "(empty)";
            double prob = bucket.getSecond().doubleValue();
            if(limit!=null&&i>=limit) {
                remaining+=prob;
            } else {
                Point point = new Point(label.toString(), prob);
                series.addPoint(point);
            }
        }
        if(remaining>0) {
            series.addPoint(new Point("(remaining)",remaining));
        }
        return series;
    }

    private static Point createPoint(String label, Number val, PointSeries drillDownData, Options parentOptions) {
        Point point;
        if(drillDownData!=null) {
            Options childOptions = new Options()
                    .setSeries(Collections.singletonList(drillDownData));
            point = new DrilldownPoint(parentOptions,childOptions)
                    .setName(label)
                    .setY(val);
        } else {
            point = new Point(label, val);;
        }
        return point;
    }

    public abstract List<? extends T> create(AbstractAttribute attribute, String attrName, Aggregations aggregations);

    public abstract AggregationChart<T> dup();

    public abstract String getType();

    public Aggregation handlePotentiallyNestedAgg(Aggregations aggregations, String attrNameWithSuffix, String attrNameNestedWithSuffix) {
        if(aggregations==null) return null; // important to stop recursion
        Aggregation agg = aggregations.get(attrNameWithSuffix);
        if(agg==null&&attrNameNestedWithSuffix!=null) {
            // try nested
            Nested nested = aggregations.get(attrNameNestedWithSuffix);
            return handlePotentiallyNestedAgg(nested.getAggregations(),attrNameWithSuffix,null);
        }
        return agg;
    }

    public Aggregation handlePotentiallyNestedAgg(Aggregations aggregations, String attrName) {
        return handlePotentiallyNestedAgg(aggregations,attrName+aggSuffix,attrName+NESTED_SUFFIX+aggSuffix);
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
            List<Pair<Number,Number>> ranges = rangeAttribute.getRanges();
            for(Pair<Number,Number> range: ranges) {
                String str=range.getFirst().toString();
                if(range.getSecond()!=null) {
                    str+="-"+range.getSecond().toString();
                } else {
                    str+="+";
                }
                dataSets.add(str);
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
        AbstractAggregation aggregation = AggregatePieChart.buildDistributionAggregation(this,attribute,attrName,null,aggSuffix);
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        if(groupedByAttrName!=null) { // handle two dimensional case (pivot)
            int groupLimit = attrNameToMaxGroupSizeMap.getOrDefault(attrName, MAXIMUM_AGGREGATION_SIZE);
            AbstractAggregation twoDimensionalAgg = createGroupedAttribute(attrName,groupedByAttrName,groupLimit,aggregation.getAggregation());
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
