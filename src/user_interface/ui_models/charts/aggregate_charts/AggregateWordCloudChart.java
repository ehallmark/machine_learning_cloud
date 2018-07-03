package user_interface.ui_models.charts.aggregate_charts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import org.elasticsearch.search.aggregations.Aggregations;
import seeding.Constants;
import user_interface.server.BigQueryServer;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.RangeAttribute;
import user_interface.ui_models.charts.aggregations.Type;
import user_interface.ui_models.charts.highcharts.HeatMapChart;
import user_interface.ui_models.charts.highcharts.WordCloudChart;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AggregateWordCloudChart extends AggregationChart<WordCloudChart> {
    private static final String AGG_SUFFIX = "_wordcloud";
    public AggregateWordCloudChart(Collection<AbstractAttribute> attributes) {
        super(false,"Word Cloud",AGG_SUFFIX, attributes, null, null, Constants.WORD_CLOUD);
    }


    @Override
    public AggregateWordCloudChart dup() {
        return new AggregateWordCloudChart(attributes);
    }

    @Override
    public List<? extends WordCloudChart> create(AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        String humanAttr = SimilarPatentServer.humanAttributeFor(attrName);
        String collectAttr = attrToCollectByAttrMap.get(attrName);
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

        parentOptions = createDataForAggregationChart(parentOptions,aggregations,attribute,attrName,title,null,false,includeBlank);

        return Collections.singletonList(new WordCloudChart(parentOptions, title, subtitle, humanSearchType));
    }


    @Override
    public String getType() {
        return "wordcloud";
    }

}
