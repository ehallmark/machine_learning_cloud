package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.TermsLookup;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filters.Filters;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;
import user_interface.ui_models.charts.aggregations.buckets.FiltersAggregation;
import user_interface.ui_models.charts.aggregations.buckets.TermsAggregation;
import user_interface.ui_models.charts.highcharts.PieChart;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

public class AggregatePieChart extends AggregationChart<PieChart> {
    private static final String AGG_SUFFIX = "_pie";
    private static final String MAX_SLICES = "maxSlices";
    protected Map<String,Integer> attrToLimitMap;
    public AggregatePieChart(Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs) {
        super(false,AGG_SUFFIX, attributes, groupByAttrs, Constants.PIE_CHART, false);
        this.attrToLimitMap=Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public AggregatePieChart dup() {
        return new AggregatePieChart(attributes,groupByAttributes);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(this.attrNames!=null) {
            this.attrNames.forEach(attr -> {
                Integer limit = SimilarPatentServer.extractInt(params, attr.replace(".","")+MAX_SLICES, null);
                if(limit!=null) attrToLimitMap.put(attr,limit);
            });
        }
    }

    @Override
    public List<? extends PieChart> create(AbstractAttribute attribute, Aggregations aggregations) {
        String attrName = attribute.getFullName();
        Integer limit = attrToLimitMap.get(attrName);
        String title = SimilarPatentServer.humanAttributeFor(attrName) + " Distribution";
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(title);

        List<Pair<String,Long>> bucketData = new ArrayList<>();
        if(aggregations.get(attrName+aggSuffix) instanceof Filters) {
            List<String> dataSets = ((DatasetAttribute) attribute).getCurrentDatasets().stream()
                    .map(e->e.getFirst()).collect(Collectors.toList());
            Filters agg = aggregations.get(attrName + aggSuffix);
            // For each entry
            int i = 0;
            for (Filters.Bucket entry : agg.getBuckets()) {
                String key = dataSets.get(i);          // bucket key
                long docCount = entry.getDocCount();            // Doc count
                bucketData.add(new Pair<>(key,docCount));
                i++;
            }
        } else {
            Terms agg = aggregations.get(attrName + aggSuffix);
            for(Terms.Bucket entry : agg.getBuckets()) {
                System.out.println("Entry: "+entry.getKeyAsString()+": "+entry.getDocCount());
                String key = entry.getKeyAsString();
                long docCount = entry.getDocCount();
                bucketData.add(new Pair<>(key,docCount));
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
        data.add(series);
        return Collections.singletonList(new PieChart(title,  "", data, combineTypesToString(searchTypes)));
    }

    @Override
    public List<AbstractAggregation> getAggregations(AbstractAttribute attribute) {
        return Collections.singletonList(
                buildDistributionAggregation(attribute,aggSuffix)
        );
    }


    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        Function<String,ContainerTag> additionalTagFunction = this::getAdditionalTagPerAttr;
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Collections.singletonList(idFromName(attrName)+MAX_SLICES);
        Function2<ContainerTag,ContainerTag,ContainerTag> combineFunction = (tag1, tag2) -> div().withClass("row").with(
                div().withClass("col-10").with(
                        tag1
                ),div().withClass("col-2").with(
                        tag2
                )
        );
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,combineFunction,true);
    }

    private ContainerTag getAdditionalTagPerAttr(String attrName) {
        attrName = idFromName(attrName);
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        label("Max Slices").attr("title", "The maximum number of slices for this pie chart.").attr("style","width: 100%;").with(
                                br(),
                                input().withId(attrName+MAX_SLICES).attr("style","height: 28px;").withName(attrName+MAX_SLICES).withType("number").withClass("form-control").withValue("20")
                        )
                )
        );
    }


    @Override
    public String getType() {
        return "pie";
    }

    public static BucketAggregation buildDistributionAggregation(AbstractAttribute attribute, String aggSuffix) {
        return buildDistributionAggregation(attribute,aggSuffix,MAXIMUM_AGGREGATION_SIZE);
    }

    public static BucketAggregation buildDistributionAggregation(AbstractAttribute attribute, String aggSuffix, int maxSize) {
        String attrName = attribute.getFullName();
        if(attribute instanceof DatasetAttribute) {
            List<Pair<String,Set<String>>> dataSets = ((DatasetAttribute) attribute).getCurrentDatasets();
            QueryBuilder[] queryBuilders = dataSets.stream().map(dataset->{
                return QueryBuilders.termsLookupQuery(attrName, new TermsLookup(((DatasetAttribute) attribute).getTermsIndex(),((DatasetAttribute) attribute).getTermsType(),dataset.getFirst(),((DatasetAttribute) attribute).getTermsPath()));
            }).toArray(size->new QueryBuilder[size]);
            return new FiltersAggregation(attrName + aggSuffix,false,null,queryBuilders);
        } else if (attribute instanceof AbstractScriptAttribute) {
            return new TermsAggregation(attrName + aggSuffix, null, ((AbstractScriptAttribute) attribute).getSortScript(), "(empty)",maxSize);
        } else {
            return new TermsAggregation(attrName + aggSuffix, attrName, null, "(empty)",maxSize);
        }
    }

}
