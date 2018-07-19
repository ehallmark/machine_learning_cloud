package user_interface.server.tools;

import lombok.Getter;
import org.elasticsearch.search.aggregations.Aggregations;
import user_interface.ui_models.attributes.AbstractAttribute;
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
    @Getter
    private Aggregations aggregations;
    public ChartTask(AggregationChart<?> chart, AbstractAttribute attribute, String attrName, Aggregations aggregations) {
        this.chart=chart;
        this.aggregations=aggregations;
        this.attribute=attribute;
        this.attrName=attrName;
    }

    @Override
    protected List<? extends AbstractChart> compute() {
        return (List<? extends AbstractChart>) chart.create(attribute,attrName,aggregations);
    }

}
