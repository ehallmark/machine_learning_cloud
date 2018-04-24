package user_interface.ui_models.charts.aggregate_charts;

import lombok.Getter;
import org.elasticsearch.search.aggregations.Aggregations;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;

import java.util.Collection;
import java.util.List;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AggregationChart<T> extends AbstractChartAttribute {
    protected static final int MAXIMUM_AGGREGATION_SIZE = 1000;
    protected final String aggSuffix;
    @Getter
    protected final boolean isTable;
    public AggregationChart(boolean isTable, String aggSuffix, Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, String name, boolean groupsPlottableOnSameChart) {
        super(attributes,groupByAttrs,name,true, groupsPlottableOnSameChart);
        this.aggSuffix=aggSuffix;
        this.isTable=isTable;
    }

    public abstract List<? extends T> create(AbstractAttribute attribute, Aggregations aggregations);

    public abstract List<AbstractAggregation> getAggregations(AbstractAttribute attribute);

    public abstract AggregationChart<T> dup();

    public abstract String getType();
}
