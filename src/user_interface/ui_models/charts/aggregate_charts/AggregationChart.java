package user_interface.ui_models.charts.aggregate_charts;

import lombok.Getter;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
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
    protected static final String NESTED_SUFFIX = "_n_";
    protected static final String BUCKET_SUFFIX = "_b_";
    protected static final String GROUP_SUFFIX = "_g_";
    protected final String aggSuffix;
    @Getter
    protected final boolean isTable;
    public AggregationChart(boolean isTable, String aggSuffix, Collection<AbstractAttribute> attributes, Collection<AbstractAttribute> groupByAttrs, String name, boolean groupsPlottableOnSameChart) {
        super(attributes,groupByAttrs,name,true, groupsPlottableOnSameChart);
        this.aggSuffix=aggSuffix;
        this.isTable=isTable;
    }

    protected String removeChartNameFromAttrName(String attrName) {
        return attrName.substring(getName().replace("[]","").length()+1);
    }

    public abstract List<? extends T> create(AbstractAttribute attribute, String attrName, Aggregations aggregations);

    public abstract List<AbstractAggregation> getAggregations(AbstractAttribute attribute, String attrName);

    public abstract AggregationChart<T> dup();

    public abstract String getType();

    public Aggregation handlePotentiallyNestedAgg(Aggregations aggregations, String attrName) {
        if(aggregations==null) return null; // important to stop recursion
        Aggregation agg = aggregations.get(attrName + aggSuffix);
        if(agg==null) {
            // try nested
            Nested nested = aggregations.get(attrName+NESTED_SUFFIX+aggSuffix);
            return handlePotentiallyNestedAgg(nested.getAggregations(),attrName);
        }
        return agg;
    }

}
