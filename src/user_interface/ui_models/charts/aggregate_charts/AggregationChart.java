package user_interface.ui_models.charts.aggregate_charts;

import com.google.gson.Gson;
import com.googlecode.wickedcharts.highcharts.options.Axis;
import com.googlecode.wickedcharts.highcharts.options.DataLabels;
import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.PixelOrPercent;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;

import org.nd4j.linalg.primitives.Pair;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;
import user_interface.ui_models.charts.aggregations.buckets.SignificantTermsAggregation;
import user_interface.ui_models.charts.highcharts.ArraySeries;
import user_interface.ui_models.charts.highcharts.DrilldownChart;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AggregationChart<T> extends AbstractChartAttribute {
    public static final int MAXIMUM_AGGREGATION_SIZE = 10000;
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

    protected String getGroupSuffix() {
        return GROUP_SUFFIX+aggSuffix;
    }

    public String getGroupByAttrName(String attrName, String groupByAttr, String suffix) {
        return attrName + groupByAttr + suffix;
    }

    // Set series in options param
    protected Options createDataForAggregationChart(Options options, Aggregations aggregations, AbstractAttribute attribute, String attrName, String title, Integer limit, boolean drilldown, boolean includeBlanks) {
        List<ArraySeries> data = new ArrayList<>();
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        final boolean isGrouped = groupedByAttrName!=null;
        if(isGrouped) {
            List<Pair<Number,ArraySeries>> drilldownData = new ArrayList<>();
            AbstractAttribute groupByAttribute = findAttribute(groupByAttributes,groupedByAttrName);
            final String groupBySuffix = getGroupSuffix();
            final String groupAggName = getGroupByAttrName(attrName,groupedByAttrName,groupBySuffix);
            final String nestedGroupAggName = getGroupByAttrName(attrName,groupedByAttrName,groupBySuffix);
            if (groupByAttribute == null) {
                throw new RuntimeException("Unable to find group by attribute: " + groupedByAttrName);
            }

            Aggregation groupAgg = handlePotentiallyNestedAgg(aggregations,groupAggName,nestedGroupAggName);
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
                    ArraySeries series = getSeriesFromAgg(nestedAggs,attribute,attrName,group,limit,includeBlanks);
                    if(series.getData()==null) {
                        System.out.println("Omitting data point. No data found for "+attrName+" grouped by "+groupedByAttrName);
                        series.setData(Collections.emptyList());
                        System.out.println("Nested aggs: "+String.join("\n",nestedAggs.getAsMap().entrySet().stream().map(e->e.getKey()+": "+new Gson().toJson(e.getValue())).collect(Collectors.toList())));
                        continue;
                    }
                    if(drilldown) {
                        drilldownData.add(new Pair<>(series.getData().stream().mapToDouble(p->((Number)p.get(1)).doubleValue()).sum(),series));
                    } else {
                        if(this instanceof AggregatePieChart) {
                            series.setSize(new PixelOrPercent(80, PixelOrPercent.Unit.PERCENT))
                                    .setInnerSize(new PixelOrPercent(60, PixelOrPercent.Unit.PERCENT));
                        }
                        data.add(series);
                    }
                    i++;
                }
            } else {
                throw new RuntimeException("Unable to cast group aggregation "+groupAggName.getClass().getName()+" to MultiBucketsAggregation.class");
            }
            if(drilldown) {
                System.out.println("Drilldown data points: "+drilldownData.size());
                options = DrilldownChart.createDrilldownChart(options, drilldownData);

            } else if(this instanceof AggregatePieChart) {
                // Create PIE Donut
                System.out.println("Creating PIE chart donut");
                data = flattenSeriesForDonutChart(data, title);
            }
        } else {
            ArraySeries series = getSeriesFromAgg(aggregations, attribute, attrName, title, limit,includeBlanks);
            data.add(series);
        }
        if(!drilldown) {
            if(this instanceof AggregateLineChart) {
                List<String> categories = data.isEmpty() ? Collections.singletonList("0")
                        : data.get(0).getData().stream().map(p -> (String)p.get(0)).collect(Collectors.toList());
                System.out.println("Categories for timeline: " + String.join(", ", categories));
                if(options.getSingleXAxis()==null) {
                    options.setxAxis(Collections.singletonList(new Axis()));
                }
                options.getSingleXAxis().setCategories(categories);
            }
            options.setSeries(new ArrayList<>());
            for(Series series : data) {
                options.addSeries(series);
            }
        }
        return options;
    }

    public static List<ArraySeries> flattenSeriesForDonutChart(List<ArraySeries> data, String seriesTitle) {
        ArraySeries combinedSeries = new ArraySeries();
        combinedSeries.setSize(new PixelOrPercent(80, PixelOrPercent.Unit.PERCENT))
                .setInnerSize(new PixelOrPercent(60, PixelOrPercent.Unit.PERCENT));
        ArraySeries series = new ArraySeries();
        series.setName(seriesTitle);
        series.setSize(new PixelOrPercent(60, PixelOrPercent.Unit.PERCENT))
                .setDataLabels(new DataLabels(true).setColor(Color.WHITE).setDistance(-30));
        for(int i = 0; i < data.size(); i++) {
            ArraySeries d = data.get(i);
            double sum = 0d;
            for(List point : d.getData()) {
                combinedSeries.addPoint(point);
                sum+=((Number)point.get(1)).doubleValue();
            }
            series.addPoint(Arrays.asList(d.getName(),sum));
        }
        return Arrays.asList(series,combinedSeries);
    }

    protected AbstractAggregation createGroupedAttribute(Request req, String attrName, String groupedByAttrName, int groupLimit, AggregationBuilder innerAgg, boolean includeBlank) {
        AbstractAttribute groupByAttribute = findAttribute(groupByAttributes,groupedByAttrName);
        if(groupByAttribute==null) {
            throw new RuntimeException("Unable to find grouping attribute attribute: "+groupedByAttrName);
        }
        if(groupByAttribute instanceof DependentAttribute) {
            ((DependentAttribute)groupByAttribute).extractRelevantInformationFromParams(req);
        }
        String groupBySuffix = getGroupSuffix();
        BucketAggregation groupAgg = AggregatePieChart.buildDistributionAggregation(this,groupByAttribute,groupByAttribute.getFullName(),attrName,groupBySuffix,groupLimit,includeBlank);
        return new AbstractAggregation() {
            @Override
            public AggregationBuilder getAggregation() {
                return groupAgg.getAggregation().subAggregation(innerAgg);
            }
        };
    }

    public static PointSeries pointSeriesFromArraySeries(ArraySeries in) {
        PointSeries ps = new PointSeries();
        if(in.getData()!=null) {
            in.getData().forEach(point->{
                ps.addPoint(new Point((String)point.get(0),(Number)point.get(1)));
            });
        }
        return ps;
    }

    protected ArraySeries getSeriesFromAgg(Aggregations aggregations, AbstractAttribute attribute, String attrName, String title, Integer limit, boolean includeBlank) {
        ArraySeries series = new ArraySeries();
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
            if(!includeBlank && label.equals("(empty)")) {
                continue;
            }
            double prob = bucket.getSecond().doubleValue();
            if(limit!=null&&(series.getData()==null?0:series.getData().size()) >= i) {
                remaining+=prob;
            } else {
                Point point = new Point(label.toString(), prob);
                series.addPoint(Arrays.asList(point.getName(),point.getY()));
            }
        }
        if(remaining>0) {
            series.addPoint(Arrays.asList("(remaining)",remaining));
        }
        return series;
    }

    public abstract List<? extends T> create(AbstractAttribute attribute, String attrName, Aggregations aggregations);

    public abstract AggregationChart<T> dup();

    public abstract String getType();

    public Aggregation handlePotentiallyNestedAgg(Aggregations aggregations, String attrNameWithSuffix, String attrNameNestedWithSuffix) {
        if(aggregations==null) return null; // important to stop recursion
        Aggregation agg = aggregations.get(attrNameWithSuffix);
        if(agg==null) {
            agg=aggregations.get(attrNameWithSuffix+ SignificantTermsAggregation.SAMPLER_SUFFIX);
            if(agg!=null) {
                aggregations = ((SingleBucketAggregation)agg).getAggregations();
                if(aggregations!=null) {
                    return handlePotentiallyNestedAgg(aggregations,attrNameWithSuffix,attrNameNestedWithSuffix);
                }
            }
        }

        if(agg==null&&attrNameNestedWithSuffix!=null) {
            // try nested
            Nested nested = aggregations.get(attrNameNestedWithSuffix);
            if(nested==null) {
                System.out.println("Attr name with suffix: "+attrNameWithSuffix);
                System.out.println("Attr name nested with suffix: "+attrNameNestedWithSuffix);
                System.out.println("Available: "+String.join("; ", aggregations.getAsMap().keySet()));
                throw new RuntimeException("Unable to find nested attribute: "+attrNameNestedWithSuffix);
            }
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
                String key = categories==null?entry.getKeyAsString():categories.get(i);
                if(key==null && entry.getKey()!=null) key = entry.getKey().toString();// bucket key
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

    public List<AbstractAggregation> getAggregations(Request req, AbstractAttribute attribute, String attrName) {
        Integer maxSlices = null;
        if(this instanceof AggregatePieChart) {
            // check for max slices
            maxSlices = ((AggregatePieChart) this).attrToLimitMap.getOrDefault(attrName, AggregatePieChart.DEFAULT_MAX_SLICES);
            boolean includeRemaining = ((AggregatePieChart) this).attrToIncludeRemainingMap.getOrDefault(attrName, false);
            if(includeRemaining) {
                maxSlices = AggregatePieChart.MAXIMUM_AGGREGATION_SIZE;
            }
            System.out.println("Max slices: " + maxSlices);
        }
        if(maxSlices==null) {
            maxSlices = AggregatePieChart.MAXIMUM_AGGREGATION_SIZE;
        }
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        AbstractAggregation aggregation = AggregatePieChart.buildDistributionAggregation(this,attribute,attrName,null,aggSuffix,maxSlices, includeBlank);
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        if(groupedByAttrName!=null) { // handle two dimensional case (pivot)
            int groupLimit = attrNameToMaxGroupSizeMap.getOrDefault(attrName, AggregatePieChart.DEFAULT_MAX_SLICES);
            AbstractAggregation twoDimensionalAgg = createGroupedAttribute(req, attrName,groupedByAttrName,groupLimit,aggregation.getAggregation(), includeBlank);
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
