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
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.metrics.min.Min;
import org.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStats;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.nd4j.linalg.primitives.Pair;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;
import user_interface.ui_models.charts.aggregations.buckets.SignificantTermsAggregation;
import user_interface.ui_models.charts.aggregations.metrics.CombinedAggregation;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.charts.highcharts.ArraySeries;
import user_interface.ui_models.charts.highcharts.DrilldownChart;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AggregationChart<T> extends AbstractChartAttribute {
    public static final int MAXIMUM_AGGREGATION_SIZE = 10000;
    public static final String NESTED_SUFFIX = "_n_";
    public static final String REVERSE_NESTED_SUFFIX = "_reverse_nest_";
    public static final String GROUP_SUFFIX = "_g_";
    protected final String aggSuffix;
    @Getter
    protected final boolean isTable;
    protected String chartTitle;

    public AggregationChart(boolean isTable, String chartTitle, String aggSuffix, Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Collection<AbstractAttribute> collectByAttrs, String name, boolean groupsPlottableOnSameChart) {
        super(attributes, groupByAttrs, collectByAttrs, name, true, groupsPlottableOnSameChart);
        this.aggSuffix = aggSuffix;
        this.isTable = isTable;
        this.chartTitle = chartTitle;
    }

    protected String getGroupSuffix() {
        return GROUP_SUFFIX + aggSuffix;
    }

    public String getGroupByAttrName(String attrName, String groupByAttr, String suffix) {
        return attrName + groupByAttr + suffix;
    }

    // Set series in options param
    protected Options createDataForAggregationChart(Options options, Aggregations aggregations, AbstractAttribute attribute, String attrName, String title, Integer limit, boolean drilldown, boolean includeBlanks) {
        List<ArraySeries> data = new ArrayList<>();
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        final boolean isGrouped = groupedByAttrName != null;
        if (isGrouped) {
            List<Pair<Number, ArraySeries>> drilldownData = new ArrayList<>();
            AbstractAttribute groupByAttribute = findAttribute(groupByAttributes, groupedByAttrName);
            final String groupBySuffix = getGroupSuffix();
            final String groupAggName = getGroupByAttrName(attrName, groupedByAttrName, groupBySuffix);
            final String nestedGroupAggName = getGroupByAttrName(attrName, groupedByAttrName, NESTED_SUFFIX + groupBySuffix);
            if (groupByAttribute == null) {
                throw new RuntimeException("Unable to find group by attribute: " + groupedByAttrName);
            }

            Aggregation groupAgg = handlePotentiallyNestedAgg(aggregations, groupAggName, nestedGroupAggName);
            if (groupAgg == null) {
                System.out.println("Group agg: " + groupAggName);
                System.out.println("Available aggs: " + String.join(", ", aggregations.getAsMap().keySet()));
                throw new NullPointerException("Group agg is null");
            }
            List<String> groupByDatasets = getCategoriesForAttribute(groupByAttribute);
            if (groupAgg instanceof MultiBucketsAggregation) {
                MultiBucketsAggregation agg = (MultiBucketsAggregation) groupAgg;
                int i = 0;
                for (MultiBucketsAggregation.Bucket entry : agg.getBuckets()) {
                    String group = groupByDatasets == null ? entry.getKeyAsString() : groupByDatasets.get(i);
                    Aggregations nestedAggs = entry.getAggregations();
                    ArraySeries series = getSeriesFromAgg(nestedAggs, attribute, attrName, group, limit, includeBlanks);
                    if (series.getData() == null) {
                        System.out.println("Omitting data point. No data found for " + attrName + " grouped by " + groupedByAttrName);
                        series.setData(Collections.emptyList());
                        System.out.println("Nested aggs: " + String.join("\n", nestedAggs.getAsMap().entrySet().stream().map(e -> e.getKey() + ": " + new Gson().toJson(e.getValue())).collect(Collectors.toList())));
                        continue;
                    }
                    if (drilldown) {
                        drilldownData.add(new Pair<>(series.getData().stream().mapToDouble(p -> ((Number) p.get(1)).doubleValue()).sum(), series));
                    } else {
                        data.add(series);
                    }
                    i++;
                }
            } else {
                throw new RuntimeException("Unable to cast group aggregation " + groupAggName.getClass().getName() + " to MultiBucketsAggregation.class");
            }
            if (drilldown) {
                boolean isHistogram = this instanceof AggregateHistogramChart;
                boolean swapAxis = attrToSwapAxesMap.getOrDefault(attrName, false);
                if (isHistogram && swapAxis) {
                    // need to regroup data across buckets
                    List<String> groups = drilldownData.stream().map(d->d.getSecond().getName()).collect(Collectors.toList());
                    if(groups.size()>0) {
                        List<String> newGroups = drilldownData.get(0).getSecond().getData().stream().map(l->(String)l.get(0)).collect(Collectors.toList());
                        int dataSize = newGroups.size();
                        List<Pair<Number,ArraySeries>> tmp = drilldownData;
                        drilldownData = IntStream.range(0, dataSize).mapToObj(i -> {
                            double total = 0;
                            List<List> newData = new ArrayList<>(dataSize);
                            for(Pair<Number,ArraySeries> pair : tmp) {
                                ArraySeries series = pair.getRight();
                                Number val = ((Number)series.getData().get(i).get(1));
                                newData.add(Arrays.asList(series.getName(), val));
                                total += val.doubleValue();
                            }
                            ArraySeries series = new ArraySeries();
                            series.setName(newGroups.get(i));
                            series.setData(newData);
                            series.setShowInLegend(true);
                            return new Pair<>((Number)total, series);
                        }).collect(Collectors.toList());
                    }
                }
                System.out.println("Drilldown data points: " + drilldownData.size());
                options = DrilldownChart.createDrilldownChart(isHistogram, swapAxis, options, drilldownData);

            } else if (this instanceof AggregatePieChart) {
                // Create PIE Donut
                System.out.println("Creating PIE chart donut");
                data = flattenSeriesForDonutChart(data, title);
            }
        } else {
            ArraySeries series = getSeriesFromAgg(aggregations, attribute, attrName, title, limit, includeBlanks);
            data.add(series);
        }
        if (!drilldown) {
            if (this instanceof AggregateLineChart) {
                List<String> categories = data.isEmpty() ? Collections.singletonList("0")
                        : data.get(0).getData().stream().map(p -> (String) p.get(0)).collect(Collectors.toList());
                System.out.println("Categories for timeline: " + String.join(", ", categories));
                if (options.getSingleXAxis() == null) {
                    options.setxAxis(Collections.singletonList(new Axis()));
                }
                options.getSingleXAxis().setCategories(categories);
            }
            options.setSeries(new ArrayList<>());
            for (Series series : data) {
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
                .setDataLabels(new DataLabels(true).setColor(Color.WHITE).setDistance(-40));
        List<String> outerColors = new ArrayList<>();
        List<String> innerColors = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            ArraySeries d = data.get(i);
            double sum = 0d;
            String color = AbstractChart.getColor(i, 0);
            outerColors.add(color);
            int p = 0;
            for (List point : d.getData()) {
                combinedSeries.addPoint(point);
                sum += ((Number) point.get(1)).doubleValue();
                String innerColor = AbstractChart.getColor(i, Math.min(90, p*10));
                innerColors.add(innerColor);
                p++;
            }
            series.addPoint(Arrays.asList(d.getName(), sum));

        }
        series.setColors(outerColors);
        combinedSeries.setColors(innerColors);
        return Arrays.asList(series, combinedSeries);
    }

    protected AbstractAggregation createGroupedAttribute(Request req, String attrName, String groupedByAttrName, int groupLimit, AggregationBuilder innerAgg, boolean includeBlank) {
        AbstractAttribute groupByAttribute = findAttribute(groupByAttributes, groupedByAttrName);
        if (groupByAttribute == null) {
            throw new RuntimeException("Unable to find grouping attribute attribute: " + groupedByAttrName);
        }
        if (groupByAttribute instanceof DependentAttribute) {
            groupByAttribute = ((DependentAttribute)groupByAttribute).dup();
            ((DependentAttribute) groupByAttribute).extractRelevantInformationFromParams(req);
        }
        String groupBySuffix = getGroupSuffix();
        BucketAggregation groupAgg = AggregatePieChart.buildDistributionAggregation(this, groupByAttribute, groupByAttribute.getFullName(), attrName, groupBySuffix, groupLimit, includeBlank, innerAgg);
        return new AbstractAggregation() {
            @Override
            public AggregationBuilder getAggregation() {
                return groupAgg.getAggregation();
            }
        };
    }

    public static PointSeries pointSeriesFromArraySeries(ArraySeries in) {
        PointSeries ps = new PointSeries();
        if (in.getData() != null) {
            in.getData().forEach(point -> {
                ps.addPoint(new Point((String) point.get(0), (Number) point.get(1)));
            });
        }
        return ps;
    }

    protected ArraySeries getSeriesFromAgg(Aggregations aggregations, AbstractAttribute attribute, String attrName, String title, Integer limit, boolean includeBlank) {
        ArraySeries series = new ArraySeries();
        Function<Aggregations, Number> subAggregationHandler = getSubAggregationHandler(attrName);
        series.setName(title);
        List<Pair<String,Number>> bucketData = extractValuesFromAggregation(aggregations,attribute,attrName,subAggregationHandler);
        double remaining = 0d;
        for (int i = 0; i < bucketData.size(); i++) {
            Pair<String, Number> bucket = bucketData.get(i);
            Object label = bucket.getFirst();
            if (label == null || label.toString().isEmpty()) label = "(empty)";
            if (!includeBlank && label.equals("(empty)")) {
                continue;
            }
            double prob = bucket.getSecond().doubleValue();
            if (limit != null && (series.getData() == null ? 0 : series.getData().size()) >= i) {
                remaining += prob;
            } else {
                Point point = new Point(label.toString(), prob);
                series.addPoint(Arrays.asList(point.getName(), point.getY()));
            }
        }
        if (remaining > 0) {
            series.addPoint(Arrays.asList("(remaining)", remaining));
        }
        return series;
    }

    public abstract List<? extends T> create(AbstractAttribute attribute, String attrName, Aggregations aggregations);

    public abstract AggregationChart<T> dup();

    public abstract String getType();

    public Aggregation handlePotentiallyNestedAgg(Aggregations aggregations, String attrNameWithSuffix, String attrNameNestedWithSuffix) {
        if (aggregations == null) return null; // important to stop recursion
        System.out.println("Handling potentially nested agg: "+attrNameWithSuffix+" with nested name: "+attrNameNestedWithSuffix);
        System.out.println("Available aggregations: "+String.join("; ", aggregations.getAsMap().keySet()));
        Aggregation agg = aggregations.get(attrNameWithSuffix);
        if (agg == null) {
            agg = aggregations.get(attrNameWithSuffix + SignificantTermsAggregation.SAMPLER_SUFFIX);
            if (agg != null) {
                aggregations = ((SingleBucketAggregation) agg).getAggregations();
                if (aggregations != null) {
                    return handlePotentiallyNestedAgg(aggregations, attrNameWithSuffix, attrNameNestedWithSuffix);
                }
            }
            // check for reverse nested
            if(agg==null) {
                agg = aggregations.get(attrNameWithSuffix+ REVERSE_NESTED_SUFFIX);
                if(agg!=null) {
                    aggregations = ((SingleBucketAggregation) agg).getAggregations();
                    if (aggregations != null) {
                        return handlePotentiallyNestedAgg(aggregations, attrNameWithSuffix, attrNameNestedWithSuffix);
                    }
                }
            }
        }

        if (agg == null && attrNameNestedWithSuffix != null) {
            // try nested
            Nested nested = aggregations.get(attrNameNestedWithSuffix);
            // check for reverse nested
            if(nested==null) {
                nested = aggregations.get(attrNameNestedWithSuffix + REVERSE_NESTED_SUFFIX);
                if(nested!=null) {
                    aggregations = nested.getAggregations();
                    if (aggregations != null) {
                        //System.out.println("Found reverse nested suffix!!: ");
                        return handlePotentiallyNestedAgg(aggregations, attrNameWithSuffix, null);
                    }
                }
            }
            if (nested == null) {
                //System.out.println("Attr name with suffix: " + attrNameWithSuffix);
                //System.out.println("Attr name nested with suffix: " + attrNameNestedWithSuffix);
                //System.out.println("Available: " + String.join("; ", aggregations.getAsMap().keySet()));
                throw new RuntimeException("Unable to find nested attribute: " + attrNameNestedWithSuffix);
            }
            return handlePotentiallyNestedAgg(nested.getAggregations(), attrNameWithSuffix, null);
        }
        return agg;
    }

    public Aggregation handlePotentiallyNestedAgg(Aggregations aggregations, String attrName) {
        return handlePotentiallyNestedAgg(aggregations, attrName + aggSuffix, attrName + NESTED_SUFFIX + aggSuffix);
    }

    protected List<String> getCategoriesForAttribute(AbstractAttribute attribute) {
        List<String> dataSets; // custom category names
        if (attribute instanceof DatasetAttribute) {
            dataSets = ((DatasetAttribute) attribute).getCurrentDatasets().stream()
                    .map(e -> e.getFirst()).collect(Collectors.toList());
        } else if (attribute instanceof RangeAttribute) {
            dataSets = new ArrayList<>();
            RangeAttribute rangeAttribute = (RangeAttribute) attribute;
            // build categories
            List<Pair<Number, Number>> ranges = rangeAttribute.getRanges();
            for (Pair<Number, Number> range : ranges) {
                String str = range.getFirst().toString();
                if (range.getSecond() != null) {
                    str += "-" + range.getSecond().toString();
                } else {
                    str += "+";
                }
                dataSets.add(str);
            }
        } else {
            dataSets = null;
        }
        return dataSets;
    }


    protected List<Pair<String, Number>> extractValuesFromAggregation(Aggregations aggregations, AbstractAttribute attribute, String attrName, Function<Aggregations, Number> subAggregationHandler) {
        Aggregation _agg = handlePotentiallyNestedAgg(aggregations, attrName);
        List<String> categories = getCategoriesForAttribute(attribute);
        List<Pair<String, Number>> bucketData = new ArrayList<>();
        if (_agg instanceof MultiBucketsAggregation) {
            MultiBucketsAggregation agg = (MultiBucketsAggregation) _agg;
            // For each entry
            int i = 0;
            for (MultiBucketsAggregation.Bucket entry : agg.getBuckets()) {
                String key = categories == null ? entry.getKeyAsString() : categories.get(i);
                if (key == null && entry.getKey() != null) key = entry.getKey().toString();// bucket key
                long docCount = entry.getDocCount();            // Doc count
                if (subAggregationHandler == null) {
                    bucketData.add(new Pair<>(key, docCount));
                } else {
                    bucketData.add(new Pair<>(key, subAggregationHandler.apply(entry.getAggregations())));
                }
                i++;
            }
        } else {
            throw new RuntimeException("Unable to cast " + _agg.getClass().getName() + " to MultiBucketsAggregation.class");
        }
        return bucketData;
    }

    public static AbstractAttribute findAttribute(Collection<AbstractAttribute> nonNestedAttributes, String attrName) {
        return nonNestedAttributes.stream().filter(attr -> attr.getFullName().equals(attrName)).limit(1).findFirst().orElse(null);
    }

    public List<AbstractAggregation> getAggregations(Request req, AbstractAttribute attribute, String attrName) {
        Integer maxSlices = null;
        if (this instanceof AggregatePieChart) {
            // check for max slices
            maxSlices = ((AggregatePieChart) this).attrToLimitMap.getOrDefault(attrName, AggregatePieChart.DEFAULT_MAX_SLICES);
            boolean includeRemaining = ((AggregatePieChart) this).attrToIncludeRemainingMap.getOrDefault(attrName, false);
            if (includeRemaining) {
                maxSlices = AggregatePieChart.MAXIMUM_AGGREGATION_SIZE;
            }
            System.out.println("Max slices: " + maxSlices);
        }
        if (maxSlices == null) {
            maxSlices = AggregatePieChart.MAXIMUM_AGGREGATION_SIZE;
        }
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        Type collectorType = attrToCollectTypeMap.get(attrName);
        String collectByAttrName = attrToCollectByAttrMap.get(attrName);
        if(collectorType==null && collectByAttrName!=null) throw new RuntimeException("Please select collector type.");
        if(collectByAttrName==null && collectorType!=null && !collectorType.equals(Type.Count)) throw new RuntimeException("Please select collect by attribute name.");

        System.out.println("Collecting by attribute: "+collectByAttrName);
        System.out.println("Collect by: "+collectorType);
        System.out.println("Available collector attrs: "+String.join("; ",attrToCollectByAttrMap.keySet()));

        AbstractAttribute collectByAttribute = collectByAttrName==null?null:findAttribute(collectByAttributes,collectByAttrName);
        if(collectByAttribute==null) {
            System.out.println("Collect by attribute could not be found: "+collectByAttrName);
        }
        if(collectByAttribute!=null && collectByAttribute instanceof DependentAttribute) {
            collectByAttribute = ((DependentAttribute) collectByAttribute).dup();
            ((DependentAttribute)collectByAttribute).extractRelevantInformationFromParams(req);
        }
        if(attribute!=null && attribute instanceof DependentAttribute) {
            attribute = ((DependentAttribute) attribute).dup();
            ((DependentAttribute) attribute).extractRelevantInformationFromParams(req);
        }
        CombinedAggregation statsAgg = new CombinedAggregation(getStatsAggName(attrName), getStatsAggName(attrName+NESTED_SUFFIX), collectByAttribute, collectorType);
        BucketAggregation attrAgg = AggregatePieChart.buildDistributionAggregation(this,attribute, attrName,null, aggSuffix, maxSlices, includeBlank, statsAgg.getAggregation());
        boolean isNested = attribute.getParent()!=null&&(!(attribute.getParent() instanceof AbstractChartAttribute))&&(!attribute.getParent().isObject());
        System.out.println("Nested? "+attribute.getName()+": "+isNested);
        if(groupedByAttrName!=null) { // handle two dimensional case (pivot)
            int groupLimit = attrNameToMaxGroupSizeMap.getOrDefault(attrName, AggregatePieChart.DEFAULT_MAX_SLICES);
            AbstractAggregation twoDimensionalAgg = createGroupedAttribute(req, attrName,groupedByAttrName,groupLimit,attrAgg.getAggregation(), includeBlank);
            return Collections.singletonList(
                    twoDimensionalAgg
            );
        } else {
            return Collections.singletonList(
                    attrAgg
            );
        }
    }

    protected Function<Aggregations, Number> getSubAggregationHandler(String attrName) {
        Type collectorType = attrToCollectTypeMap.get(attrName);
        String collectByAttrName = attrToCollectByAttrMap.get(attrName);

        final String statsAggName = getStatsAggName(attrName);
        final String nestedStatsAggName = getStatsAggName(attrName+NESTED_SUFFIX);

        return collectByAttrName == null ? null : subAggs -> {
            final Aggregation sub = handlePotentiallyNestedAgg(subAggs, statsAggName, nestedStatsAggName);
            Number val;
            switch (collectorType) {
                case Max: {
                    val = ((Max) sub).getValue();
                    break;
                }
                case Min: {
                    val = ((Min) sub).getValue();
                    break;
                }
                case Sum: {
                    val = ((Sum) sub).getValue();
                    break;
                }
                case Average: {
                    val = ((Avg) sub).getValue();
                    break;
                }
                case Cardinality: {
                    val = ((Cardinality) sub).getValue();
                    break;
                }
                case Count: {
                    val = ((ValueCount) sub).getValue();
                    break;
                }
                case Variance: {
                    val = ((ExtendedStats) sub).getVariance();
                    break;
                }
                case StdDeviation: {
                    val = ((ExtendedStats) sub).getStdDeviation();
                    break;
                }
                default: {
                    val = null;
                    break;
                }
            }
            return val;
        };
    }

}
