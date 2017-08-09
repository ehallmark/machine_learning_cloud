package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.filters.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractAttribute<T> {
    protected Collection<AbstractFilter.FilterType> filterTypes;
    public AbstractAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        this.filterTypes=filterTypes;
    }

    public abstract String getName();

    public Tag getOptionsTag() { return div(); }

    public abstract String getType();

    public abstract AbstractFilter.FieldType getFieldType();


    public boolean supportedByElasticSearch() {
        return true;
    }

    public Collection<AbstractFilter> createFilters() {
        return filterTypes.stream().map(filterType->{
            AbstractFilter filter;
            switch(filterType) {
                case BoolFalse: {
                    filter = new AbstractBooleanExcludeFilter(this, filterType);
                    break;
                }
                case BoolTrue: {
                    filter = new AbstractBooleanIncludeFilter(this, filterType);
                    break;
                }
                case Exclude: {
                    filter = new AbstractExcludeFilter(this, filterType, getFieldType(), null);
                    break;
                }
                case Include: {
                    filter = new AbstractIncludeFilter(this, filterType, getFieldType(), null);
                    break;
                }
                case LessThan: {
                    filter = new AbstractLessThanFilter(this, filterType);
                    break;
                }
                case GreaterThan: {
                    filter = new AbstractGreaterThanFilter(this, filterType);
                    break;
                }
                case Between: {
                    filter = new AbstractBetweenFilter(this, filterType);
                    break;
                } case AdvancedKeyword: {
                    filter = new AdvancedKeywordFilter(this, filterType);
                    break;
                } case Nested: {
                    if(!(this instanceof NestedAttribute)) throw new RuntimeException("Only nested attributes support nested filterType");
                    filter = new AbstractNestedFilter((NestedAttribute)this, filterType);
                    break;
                } default: {
                    filter = null;
                }
            }
            return filter;
        }).filter(filter->filter!=null).collect(Collectors.toList());
    }

}
