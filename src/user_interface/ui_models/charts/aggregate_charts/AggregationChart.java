package user_interface.ui_models.charts.aggregate_charts;

import org.elasticsearch.action.search.SearchResponse;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.aggregations.AbstractAggregation;

import java.util.Collection;
import java.util.List;

/*
 * Created by Evan on 6/17/2017.
 */
public abstract class AggregationChart<T> extends AbstractChartAttribute {
    protected static final int MAXIMUM_AGGRAGATION_SIZE = 1000;
    protected final String aggSuffix;
    public AggregationChart(String aggSuffix, Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, String name, boolean groupsPlottableOnSameChart) {
        super(attributes,groupByAttrs,name,true, groupsPlottableOnSameChart);
        this.aggSuffix=aggSuffix;
    }

    public abstract List<? extends T> create(AbstractAttribute attribute, SearchResponse response);

    public abstract List<AbstractAggregation> getAggregations(AbstractAttribute attribute);
}
