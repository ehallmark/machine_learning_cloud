package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import spark.Request;
import user_interface.server.BigQueryServer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.highcharts.HeatMapChart;
import user_interface.ui_models.charts.highcharts.WordCloudChart;

import java.util.*;
import java.util.function.Function;

import static j2html.TagCreator.*;
import static j2html.TagCreator.br;
import static j2html.TagCreator.input;

public class AggregateWordCloudChart extends AggregationChart<WordCloudChart> {
    private static final String AGG_SUFFIX = "_wordcloud";
    protected Map<String,Integer> attrToLimitMap;
    public AggregateWordCloudChart(Collection<AbstractAttribute> attributes) {
        super(false,"Word Cloud",AGG_SUFFIX, attributes, null, null, Constants.WORD_CLOUD);
        this.attrToLimitMap = Collections.synchronizedMap(new HashMap<>());
    }


    @Override
    public AggregateWordCloudChart dup() {
        return new AggregateWordCloudChart(attributes);
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {
        super.extractRelevantInformationFromParams(params);
        if(this.attrNames!=null) {
            this.attrNames.forEach(attr -> {
                Integer limit = SimilarPatentServer.extractInt(params, getMaxSlicesField(attr), AggregatePieChart.DEFAULT_MAX_SLICES);
                if(limit!=null) attrToLimitMap.put(attr,limit);
            });
        }
    }


    @Override
    public List<? extends WordCloudChart> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        String humanAttr = SimilarPatentServer.humanAttributeFor(attrName);
        String collectAttr = attrToCollectByAttrMap.get(attrName);
        Integer limit = attrToLimitMap.getOrDefault(attrName, AggregatePieChart.DEFAULT_MAX_SLICES);
        if(limit > MAXIMUM_AGGREGATION_SIZE) {
            limit = MAXIMUM_AGGREGATION_SIZE;
        }
        if(collectAttr==null) {
            collectAttr = "Occurrences";
        } else {
            throw new RuntimeException("Unable to use collect by attributes with word cloud chart.");
        }
        String humanSearchType = collectAttr;
        String title = humanAttr + " "+chartTitle;

        String groupedByAttrName = attrNameToGroupByAttrNameMap.get(attrName);
        String subtitle = "";
        final boolean isGrouped = groupedByAttrName!=null;
        if(isGrouped) {
            throw new RuntimeException("Unable to use group by attributes with word cloud chart.");
        }
        Options parentOptions = new Options();
        boolean includeBlank = attrNameToIncludeBlanksMap.getOrDefault(attrName, false);

        parentOptions = createDataForAggregationChart(parentOptions,aggregations,attribute,attrName,title,limit,false,includeBlank);

        return Collections.singletonList(new WordCloudChart(parentOptions, title, subtitle, humanSearchType));
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        Function<String,ContainerTag> additionalTagFunction = this::getAdditionalTagPerAttr;
        Function<String,List<String>> additionalInputIdsFunction = attrName -> Collections.singletonList(getMaxSlicesField(attrName));
        Function2<ContainerTag,ContainerTag,ContainerTag> combineFunction = (tag1, tag2) -> div().withClass("row").with(
                div().withClass("col-12").with(
                        tag1
                ),div().withClass("col-12").with(
                        tag2
                )
        );
        return super.getOptionsTag(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,combineFunction,true);
    }

    private ContainerTag getAdditionalTagPerAttr(String attrName) {
        return div().withClass("row").with(
                div().withClass("col-12").with(
                        label("Max Elements").attr("title", "The maximum number of elements for this word cloud chart.").attr("style","width: 100%;").with(
                                br(),
                                input().withId(getMaxSlicesField(attrName)).withName(getMaxSlicesField(attrName)).withType("number").withClass("form-control").withValue("20")
                        )
                )
        );
    }



    @Override
    public String getType() {
        return "wordcloud";
    }

}
