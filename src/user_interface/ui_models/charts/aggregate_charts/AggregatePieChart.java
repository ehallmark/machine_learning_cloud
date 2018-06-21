package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.TermsLookup;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.google.elasticsearch.attributes.DateRangeAttribute;
import seeding.google.elasticsearch.attributes.SignificantTermsAttribute;
import spark.Request;
import user_interface.server.BigQueryServer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.aggregations.buckets.*;
import user_interface.ui_models.charts.highcharts.PieChart;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.time.temporal.TemporalAmount;
import java.util.*;
import java.util.function.Function;

import static j2html.TagCreator.*;

public class AggregatePieChart extends AggregationChart<PieChart> {
    private static final String AGG_SUFFIX = "_pie";
    public static final int DEFAULT_MAX_SLICES = 30;

    protected Map<String,Integer> attrToLimitMap;
    protected Map<String,Boolean> attrToIncludeRemainingMap;
    public AggregatePieChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, Collection<AbstractAttribute> collectAttrs) {
        super(false,"Distribution",AGG_SUFFIX, attributes, groupByAttrs, collectAttrs,Constants.PIE_CHART, false);
        this.attrToLimitMap=Collections.synchronizedMap(new HashMap<>());
        this.attrToIncludeRemainingMap = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public AggregatePieChart dup() {
        return new AggregatePieChart(attributes,groupByAttributes,collectByAttributes);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(this.attrNames!=null) {
            this.attrNames.forEach(attr -> {
                Integer limit = SimilarPatentServer.extractInt(params, getMaxSlicesField(attr), DEFAULT_MAX_SLICES);
                if(limit!=null) attrToLimitMap.put(attr,limit);
                boolean includeRemaining = SimilarPatentServer.extractBool(params, getIncludeRemainingField(attr));
                attrToIncludeRemainingMap.put(attr, includeRemaining);
            });
        }
    }

    @Override
    public List<? extends PieChart> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        String title = SimilarPatentServer.humanAttributeFor(attrName) + " "+chartTitle;
        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        Integer limit = attrToLimitMap.getOrDefault(attrName, DEFAULT_MAX_SLICES);
        Type collectorType = attrToCollectTypeMap.getOrDefault(attrName, Type.Count);
        String collectAttr = attrToCollectByAttrMap.get(attrName);
        if(collectAttr==null) {
            collectAttr = "Assets";
        } else {
            collectAttr = BigQueryServer.fullHumanAttributeFor(collectAttr);
        }
        String subtitle = "";
        final boolean isGrouped = groupedByAttrName!=null;
        if(isGrouped) {
            subtitle = "Grouped by "+SimilarPatentServer.humanAttributeFor(groupedByAttrName);
        }
        Options parentOptions = new Options();
        boolean drilldown = attrToDrilldownMap.getOrDefault(attrName,false);
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);
        boolean includedRemaining = attrToIncludeRemainingMap.getOrDefault(attrName, false);
        if(!includedRemaining) {
            limit = null; // turns off accumulating remaining pie piece
        }
        parentOptions = createDataForAggregationChart(parentOptions, aggregations,attribute,attrName,title,limit,drilldown,includeBlank);
        return Collections.singletonList(new PieChart(parentOptions, title,  subtitle, collectAttr, collectorType));
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        Function<String,ContainerTag> additionalTagFunction = this::getAdditionalTagPerAttr;
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Arrays.asList(getDrilldownAttrFieldName(attrName),getIncludeRemainingField(attrName),getMaxSlicesField(attrName));
        Function2<ContainerTag,ContainerTag,ContainerTag> combineFunction = (tag1, tag2) -> div().withClass("row").with(
                div().withClass("col-8").with(
                        tag1
                ),div().withClass("col-4").with(
                        tag2
                )
        );
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,combineFunction,true);
    }

    private ContainerTag getAdditionalTagPerAttr(String attrName) {
        return div().withClass("row").with(
                div().withClass("col-4").with(
                        label("Max Slices").attr("title", "The maximum number of slices for this pie chart.").attr("style","width: 100%;").with(
                                br(),
                                input().withId(getMaxSlicesField(attrName)).withName(getMaxSlicesField(attrName)).withType("number").withClass("form-control").withValue("20")
                        )
                ), div().withClass("col-4").with(
                        label("Drilldown").attr("title","Plot groups using drilldowns.").with(
                                br(),
                                input().withId(getDrilldownAttrFieldName(attrName)).withValue("off").withName(getDrilldownAttrFieldName(attrName)).withType("checkbox")
                        )
                ), div().withClass("col-4").with(
                        label("Include Remaining").attr("title", "Including remaining counts in the pie chart.").with(
                                br(),
                                input().withId(getIncludeRemainingField(attrName)).withName(getIncludeRemainingField(attrName)).withType("checkbox").withValue("off")
                        )
                )
        );
    }

    private static String cancelOtherCheckbox(String otherId) {
        return "$('#"+otherId+"').prop('checked', false);";
    }


    @Override
    public String getType() {
        return "pie";
    }


    public static BucketAggregation buildDistributionAggregation(AggregationChart<?> chart, AbstractAttribute attribute, String attrName, String aggPrefix, String aggSuffix, int maxSize, boolean includeBlank, AggregationBuilder innerAgg) {
        System.out.println("Building distribution agg for: "+attribute.getFullName()+" with suffix "+aggSuffix);
        boolean isNested = attribute.getParent()!=null&&(!(attribute.getParent() instanceof AbstractChartAttribute))&&(!attribute.getParent().isObject());
        final Object missingVal;
        if(includeBlank && (attribute.getType().equals("text")||attribute.getType().equals("keyword"))) {
            missingVal = "(empty)";
        } else {
            missingVal = null;
        }

        final String aggNameWithPrefix = (aggPrefix==null?"":aggPrefix)+attrName;
        BucketAggregation aggregation;
        if(attribute.getFieldType().equals(AbstractFilter.FieldType.Date)) {
            System.out.println("Building datehistogram for: "+attrName);
            LocalDate xMin = null;
            LocalDate xMax = null;
            if(chart instanceof AggregateLineChart) {
                xMin = ((AggregateLineChart) chart).getMin(attrName);
                xMax = ((AggregateLineChart) chart).getMax(attrName);
            }
            // override xMin and xMax if null
            if(!(attribute instanceof DateRangeAttribute)) {
                throw new RuntimeException("Attribute "+attribute.getFullName()+" is not a DateRangeAttribute.");
            }
            if(xMin==null) {
                xMin = ((DateRangeAttribute)attribute).getMinDate();
            }
            if(xMax==null) {
                xMax = ((DateRangeAttribute)attribute).getMaxDate();
            }
            final String dateFormat = ((DateRangeAttribute)attribute).dateFormatString();
            final TemporalAmount temporalAmount = ((DateRangeAttribute)attribute).timeInterval();
            if (attribute instanceof AbstractScriptAttribute) {
                aggregation = new DateHistogramAggregation(aggNameWithPrefix + aggSuffix, null, ((AbstractScriptAttribute) attribute).getSortScript(), xMin,xMax,temporalAmount,dateFormat, null);
            } else {
                aggregation = new DateHistogramAggregation(aggNameWithPrefix + aggSuffix, attrName, null, xMin,xMax,temporalAmount,dateFormat,null);
            }
        } else if(attribute instanceof DatasetAttribute) {
            System.out.println("Building filters aggregations (datasets) for: "+attrName);
            List<String> dataSetIds = ((DatasetAttribute) attribute).getCurrentIds();
            QueryBuilder[] queryBuilders = dataSetIds.stream().map(id -> {
                return QueryBuilders.termsLookupQuery(((DatasetAttribute) attribute).getTermsName(), new TermsLookup(((DatasetAttribute) attribute).getTermsIndex(), ((DatasetAttribute) attribute).getTermsType(), id, ((DatasetAttribute) attribute).getTermsPath()));
            }).toArray(size -> new QueryBuilder[size]);
            aggregation = new FiltersAggregation(aggNameWithPrefix + aggSuffix, false, null, queryBuilders);
            System.out.println("Dataset aggregation: "+aggregation.toString());
        } else if (attribute instanceof RangeAttribute) {
            System.out.println("Building range for: " + attrName);
            RangeAttribute rangeAttribute = (RangeAttribute) attribute;
            if (attribute instanceof AbstractScriptAttribute) {
                aggregation = new HistogramAggregation(aggNameWithPrefix + aggSuffix, null, ((AbstractScriptAttribute) attribute).getSortScript(), rangeAttribute.getRanges(), rangeAttribute.missing());
            } else {
                aggregation = new HistogramAggregation(aggNameWithPrefix + aggSuffix, attrName, null, rangeAttribute.getRanges(), rangeAttribute.missing());
            }
        } else if (attribute instanceof SignificantTermsAttribute) {
            System.out.println("Using significant terms bucketing for attr: "+attrName);
            if(attribute.getNestedFields()!=null) {
                attrName = attrName+".raw";
            }
            aggregation = new SignificantTermsAggregation(aggNameWithPrefix + aggSuffix, attrName, null, missingVal, maxSize);

        } else {
            if (attribute instanceof AbstractScriptAttribute) {
                aggregation = new TermsAggregation(aggNameWithPrefix + aggSuffix, null, ((AbstractScriptAttribute) attribute).getSortScript(), missingVal, maxSize);
            } else {
                String fieldName = attrName;
                if (attribute.getType().equals("text") && attribute.getNestedFields() != null) {
                    fieldName += ".raw";
                    System.out.println("Using raw field name: "+fieldName);
                }
                aggregation = new TermsAggregation(aggNameWithPrefix + aggSuffix, fieldName, null, missingVal, maxSize);
            }
        }
        if(innerAgg!=null) {
            if(isNested) {
                // need to break out of nested
                innerAgg = AggregationBuilders.reverseNested(innerAgg.getName()+REVERSE_NESTED_SUFFIX)
                        .subAggregation(innerAgg);
            }
            aggregation.getAggregation().subAggregation(innerAgg);
        }

        if(isNested) {
            String name = aggNameWithPrefix+NESTED_SUFFIX+aggSuffix;
            System.out.println("Is Parent Object: "+attribute.getParent().isObject());
            System.out.println("Parent attr name: "+attribute.getParent().getFullName());
            System.out.println("Parent attr class: "+attribute.getParent().getClass().getName());
            System.out.println("Nested attribute for aggregation: "+attribute.getFullName());
            System.out.println("Nested aggregation name: "+name);
            return new BucketAggregation() {
                @Override
                public AggregationBuilder getAggregation() {
                    return AggregationBuilders.nested(name,attribute.getParent().getName())
                            .subAggregation(aggregation.getAggregation());
                }
            };
        } else {
            return aggregation;
        }
    }

}
