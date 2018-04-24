package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.TermsLookup;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.nd4j.linalg.primitives.Pair;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.dataset_lookup.DatasetAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;
import user_interface.ui_models.charts.aggregations.buckets.BucketAggregation;
import user_interface.ui_models.charts.aggregations.buckets.FiltersAggregation;
import user_interface.ui_models.charts.aggregations.buckets.TermsAggregation;
import user_interface.ui_models.charts.highcharts.PieChart;

import java.util.*;
import java.util.stream.Collectors;

public class AggregatePieChart extends AggregationChart<PieChart> {
    private static final String AGG_SUFFIX = "_pie";
    private static final String MAX_SLICES = "maxSlices";
    protected Map<String,Integer> attrToLimitMap;
    public AggregatePieChart(Collection<AbstractAttribute> attributes, String name) {
        super(false,AGG_SUFFIX, attributes, Collections.emptyList(), name, false);
    }

    @Override
    public AggregatePieChart dup() {
        return new AggregatePieChart(attributes,name);
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
        Aggregation agg = aggregations.get(attrName + aggSuffix);
        List<Map<String,Object>> bucketData = (List<Map<String,Object>>) agg.getMetaData().get("buckets");
        String title = SimilarPatentServer.humanAttributeFor(attrName) + " Distribution";
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(title);
        List<String> dataSets;
        if(attribute instanceof DatasetAttribute) {
            dataSets = ((DatasetAttribute) attribute).getCurrentDatasets().stream()
                    .map(e->e.getFirst()).collect(Collectors.toList());
        } else {
            dataSets = null;
        }
        Integer limit = attrToLimitMap.get(attrName);
        double remaining = 0d;
        for(int i = 0; i < bucketData.size(); i++) {
            Map<String,Object> bucket = bucketData.get(i);
            Object label = dataSets==null?bucket.get("key"):dataSets.get(i);
            if(label==null||label.toString().isEmpty()) label = "(empty)";
            double prob = ((Number)bucket.get("doc_count")).doubleValue();
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

    public static BucketAggregation buildDistributionAggregation(AbstractAttribute attribute, String aggSuffix) {
        String attrName = attribute.getFullName();
        if(attribute instanceof DatasetAttribute) {
            List<Pair<String,Set<String>>> dataSets = ((DatasetAttribute) attribute).getCurrentDatasets();
            QueryBuilder[] queryBuilders = dataSets.stream().map(dataset->{
                return QueryBuilders.termsLookupQuery(attrName, new TermsLookup(((DatasetAttribute) attribute).getTermsIndex(),((DatasetAttribute) attribute).getTermsType(),dataset.getFirst(),((DatasetAttribute) attribute).getTermsPath()));
            }).toArray(size->new QueryBuilder[size]);
            return new FiltersAggregation(attrName + aggSuffix,false,null,queryBuilders);
        } else if (attribute instanceof AbstractScriptAttribute) {
            return new TermsAggregation(attrName + aggSuffix, null, ((AbstractScriptAttribute) attribute).getSortScript(), "(empty)",MAXIMUM_AGGRAGATION_SIZE);
        } else {
            return new TermsAggregation(attrName + aggSuffix, attrName, null, "(empty)",MAXIMUM_AGGRAGATION_SIZE);
        }
    }

}
