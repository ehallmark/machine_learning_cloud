package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.elasticsearch.index.query.QueryBuilder;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.charts.ChartAttribute;
import user_interface.ui_models.filters.*;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.p;
import static j2html.TagCreator.span;

import java.util.Arrays;
import java.util.List;
/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractAttribute {
    protected Collection<AbstractFilter.FilterType> filterTypes;
    @Getter @Setter
    protected boolean isObject = false;
    @Getter @Setter
    protected AbstractAttribute parent;

    public AbstractAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        this.filterTypes=filterTypes;
    }

    public abstract String getName();

    public String getScope() {
        return null;
    }

    public String getMongoDBName() {
        return getFullName();
    }

    public String getFullName() {
        return parent==null? getName() : (parent.getName().replaceAll("[\\[\\]]","") + "." + getName()).trim();
    }

    public QueryBuilder getQueryScope() {
        return null;
    }

    public String getRootName() {
        return parent==null? getName() : parent.getRootName();
    }

    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) { return div(); }

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

    public Tag getDescription() { return getDescription(null); }
    public Tag getDescription(AbstractFilter filter) {
        if(filter!=null) {
            String prefix;
            String name = createSimpleNameText(getFullName());
            if(filter instanceof AbstractBooleanExcludeFilter || filter instanceof AbstractBooleanIncludeFilter) {
                prefix = " ";
            } else if(Arrays.asList("a","e","i","o","u","h").contains(name.substring(0,1))) {
                prefix = " an ";
            } else {
                prefix = " a ";
            }
            return (filter instanceof AbstractNestedFilter ? div() : span()).withText(prefix+Constants.ATTRIBUTE_DESCRIPTION_MAP.getOrDefault(getFullName(),name+"."));
        } else {
            String prefix;
            if(parent==null) {
                prefix = "The ";
                if(getFieldType().equals(AbstractFilter.FieldType.Boolean)) {
                    prefix += "asset is ";
                }
            } else {
                prefix = "";
            }
            String text = prefix+Constants.ATTRIBUTE_DESCRIPTION_MAP.getOrDefault(getFullName(),createSimpleNameText(getFullName())+".");
            if(parent!=null) text = capitalize(text);
            return (parent!=null?div():span()).withText(text);
        }
    }
    protected static String capitalize(String str) {
        return str==null||str.length()==0 ? str : (str.substring(0,1).toUpperCase()+str.substring(1));
    }

    private static String createSimpleNameText(String name) {
        if(name.contains(".")) {
            String[] split = name.split("\\.",2);
            return createSimpleNameText(split[1])+" of "+theOrAnd(split[0])+" "+singularize(createSimpleNameText(split[0]));
        } else {
            return SimilarPatentServer.humanAttributeFor(name).toLowerCase();
        }
    }

    private static String theOrAnd(String rootName) {
        if(Constants.NESTED_ATTRIBUTES.contains(rootName)) {
            if(Arrays.asList("a","e","i","o","u","h").contains(rootName.substring(0,1))) {
                return "an";
            } else {
                return "a";
            }
        }
        else return "the";
    }

    private static String singularize(String name) {
        if(name.endsWith("es")&&name.length()>2) return name.substring(0,name.length()-2);
        else if(name.endsWith("s")&&name.length()>1) return name.substring(0,name.length()-1);
        return name;
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
