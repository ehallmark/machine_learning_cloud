package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
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
public abstract class AbstractFilter<T> extends AbstractAttribute<T> implements DependentAttribute {

    public enum FilterType {
        Include, Exclude, GreaterThan, LessThan, BoolTrue, BoolFalse, Between, Nested, AdvancedKeyword
    }

    public static boolean isPrefix(FilterType type) {
        if(Arrays.asList(FilterType.Include,FilterType.Exclude,FilterType.BoolTrue,FilterType.BoolFalse).contains(type)) return true;
        return false;
    }

    public enum FieldType {
        Text, Multiselect, Select, Integer, Double, Date, Boolean, AdvancedKeyword, NestedObject, Object
    }

    protected AbstractAttribute<?> attribute;
    @Getter
    protected FilterType filterType;
    @Setter @Getter
    protected AbstractFilter parent;
    public AbstractFilter(AbstractAttribute<?> attribute, FilterType filterType) {
        super(Arrays.asList(filterType));
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

    public String getFullPrerequisite() {
         return (parent==null?"":parent.getFullPrerequisite()+".")+getPrerequisite();
    }

    @Override
    public abstract Tag getOptionsTag();

    @Override
    public Collection<String> getAllValues() {
        return attribute.getAllValues();
    }

    @Override
    public String getType() {
        return attribute.getType();
    }

    @Override
    public String getName() {
        if(parent==null) {
            return getPrerequisite()+filterType.toString()+Constants.FILTER_SUFFIX;
        } else {
            // nested form field
            return parent.getName().replaceAll("[\\[\\]]","")+"["+getPrerequisite()+filterType.toString()+Constants.FILTER_SUFFIX.replaceAll("[\\[\\]\\]]","")+"]"+"[]";
        }
    }

    @Override
    public FieldType getFieldType() { return attribute.getFieldType(); }
}
