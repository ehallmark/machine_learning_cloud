package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.NonNull;
import org.elasticsearch.index.query.QueryBuilder;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractFilter<T> extends DependentAttribute<T> {

    public enum FilterType {
        Include, Exclude, GreaterThan, LessThan, BoolTrue, BoolFalse, Between, Nested, AdvancedKeyword
    }

    public enum FieldType {
        Text, Multiselect, Select, Integer, Double, Date, Boolean, AdvancedKeyword, NestedObject, Object
    }

    protected AbstractAttribute<?> attribute;
    protected FilterType filterType;
    public AbstractFilter(@NonNull AbstractAttribute<?> attribute, FilterType filterType) {
        this.attribute=attribute;
        this.filterType=filterType;
    }

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(getPrerequisite());
    }

    public String getPrerequisite() {
        return attribute.getName();
    }

    public boolean isActive() { return true; }

    public abstract QueryBuilder getFilterQuery();

    public boolean contributesToScore() { return false; }

    @Override
    public abstract Tag getOptionsTag();

    @Override
    public String getType() {
        return attribute.getType();
    }

    @Override
    public String getName() {
        return getPrerequisite()+filterType.toString()+Constants.FILTER_SUFFIX;
    }

    @Override
    public FieldType getFieldType() { return attribute.getFieldType(); }
}
