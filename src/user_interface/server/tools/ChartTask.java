package user_interface.server.tools;

import lombok.Getter;
import org.elasticsearch.search.aggregations.Aggregations;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.charts.aggregate_charts.AggregationChart;
import user_interface.ui_models.charts.highcharts.AbstractChart;

import java.util.List;
import java.util.concurrent.RecursiveTask;

public class ChartTask extends RecursiveTask<List<? extends AbstractChart>> {

    @Getter
    private AggregationChart<?> chart;
    @Getter
    private AbstractAttribute attribute;
    @Getter
    private String attrName;
    private Request req;
    @Getter
    private Aggregations aggregations;
    @Getter
    private AbstractAttribute groupByAttribute;
    @Getter
    private AbstractAttribute collectByAttribute;
    public ChartTask(Request req, AggregationChart<?> chart, String attrName, AbstractAttribute attribute, AbstractAttribute groupByAttribute, AbstractAttribute collectByAttribute, Aggregations aggregations) {
        this.chart=chart;
        this.req=req;
        this.collectByAttribute=collectByAttribute;
        this.aggregations=aggregations;
        this.groupByAttribute=groupByAttribute;
        this.attribute=attribute;
        this.attrName=attrName;
        if(this.groupByAttribute != null && this.groupByAttribute instanceof DependentAttribute) {
            this.groupByAttribute = ((DependentAttribute) this.groupByAttribute).dup();
            ((DependentAttribute) this.groupByAttribute).extractRelevantInformationFromParams(req);
        }
        if(this.collectByAttribute != null && this.collectByAttribute instanceof DependentAttribute) {
            this.collectByAttribute = ((DependentAttribute) this.collectByAttribute).dup();
            ((DependentAttribute) this.groupByAttribute).extractRelevantInformationFromParams(req);
        }
        if(this.attribute != null && this.attribute instanceof DependentAttribute) {
            this.attribute = ((DependentAttribute) this.attribute).dup();
            ((DependentAttribute) this.attribute).extractRelevantInformationFromParams(req);
        }
    }

    @Override
    protected List<? extends AbstractChart> compute() {
        return (List<? extends AbstractChart>) chart.create(req, attrName, attribute, groupByAttribute, collectByAttribute, aggregations);
    }

}
