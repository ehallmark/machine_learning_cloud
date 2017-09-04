package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.filters.*;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.p;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractAttribute {
    protected Collection<AbstractFilter.FilterType> filterTypes;
    @Getter @Setter
    protected AbstractAttribute parent;

    public AbstractAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        this.filterTypes=filterTypes;
    }

    public abstract String getName();

    public String getMongoDBName() {
        return getFullName();
    }

    public String getFullName() {
        return parent==null? getName() : (parent.getName() + "." + getName());
    }

    public String getRootName() {
        return parent==null? getName() : parent.getRootName();
    }

    public Tag getOptionsTag() { return div(); }

    public abstract String getType();

    public Map<String,Object> getNestedFields() {
        return null;
    }

    public abstract AbstractFilter.FieldType getFieldType();

    // for populating select dropdown filters
    public Collection<String> getAllValues() {
        return Collections.emptyList();
    }

    public boolean isNotYetImplemented() { return false; }

    public Tag getDescription() { return p(Constants.ATTRIBUTE_DESCRIPTION_MAP.getOrDefault(getName(),"The " +createSimpleNameText(getFullName())+".")); }


    private static String createSimpleNameText(String name) {
        if(name.contains(".")) {
            String[] split = name.split("\\.",2);
            return createSimpleNameText(split[1]) + " of " + createSimpleNameText(split[0]);
        } else {
            return SimilarPatentServer.humanAttributeFor(name).toLowerCase();
        }
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
                }
                case AdvancedKeyword: {
                    filter = new AdvancedKeywordFilter(this, filterType);
                    break;
                }
                case PrefixExclude: {
                    filter = new AbstractPrefixExcludeFilter(this, filterType, getFieldType(), null);
                    break;
                }
                case PrefixInclude: {
                    filter = new AbstractPrefixIncludeFilter(this, filterType, getFieldType(), null);
                    break;
                }
                case Nested: {
                    if(!(this instanceof NestedAttribute)) throw new RuntimeException("Only nested attributes support nested filterType");
                    filter = new AbstractNestedFilter((NestedAttribute)this);
                    break;
                }
                default: {
                    filter = null;
                }
            }
            return filter;
        }).filter(filter->filter!=null).collect(Collectors.toList());
    }

}
