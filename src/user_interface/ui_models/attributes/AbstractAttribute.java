package user_interface.ui_models.attributes;

import data_pipeline.helpers.Function2;
import j2html.tags.ContainerTag;
import j2html.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.index.query.QueryBuilder;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.charts.AbstractChartAttribute;
import user_interface.ui_models.charts.highcharts.AbstractChart;
import user_interface.ui_models.filters.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.span;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractAttribute {
    protected Collection<AbstractFilter.FilterType> filterTypes;
    public AbstractAttribute formParent;
    public AbstractAttribute clone() {
        try {
            return this.getClass().newInstance();
        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("For class: "+this.getClass().getName());
        }
        return null;
    }

    @Getter @Setter
    protected AbstractAttribute parent;
    public AbstractAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        this.filterTypes=filterTypes;
    }

    public boolean isDisplayable() {
        return true;
    }

    public abstract String getName();

    public String getAttributeId() {
        return "attribute_"+getFullName().replace(".","_");
    }

    public List<String> getInputIds() {
        return null;
    }

    public String getScope() {
        return null;
    }

    public String getMongoDBName() {
        return getFullName();
    }

    public String getFullName() {
        return parent==null? getName() : (parent.getFullName().replaceAll("[\\[\\]]","") + "." + getName()).trim();
    }

    public boolean isObject() {
        return false;
    }

    public QueryBuilder getQueryScope() {
        return null;
    }

    public String getRootName() {
        return parent==null? getName() : parent.getRootName();
    }

    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        return div().with(div().withClass("attribute").withId(getAttributeId()));
    }

    public ContainerTag getOptionsTag(Function<String,Boolean> userRoleFunction, Function<String,ContainerTag> additionalTagFunction, Function<String,List<String>> additionalInputIdsFunction, Function2<ContainerTag,ContainerTag,ContainerTag> combineTagFunction, boolean perAttr, boolean loadChildren, Map<String,String> idToTagMap) {
        String collapseId = "collapse-filters-"+getFullName().replaceAll("[\\[\\].]","");
        String styleString = "margin-left: 5%; margin-right: 5%; display: none;";
        Tag childTag;
        List<String> inputIds = new ArrayList<>();
        if(getInputIds()!=null) {
            inputIds.addAll(getInputIds());
        }
        if(this instanceof NestedAttribute && ! (this instanceof AbstractChartAttribute) && perAttr) {
            childTag = ((NestedAttribute) this).getNestedOptions(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,combineTagFunction, perAttr, loadChildren, idToTagMap);
        } else {
            System.out.println("Class: "+this.getClass().getName());
            if(this instanceof NestedAttribute) {
                childTag = ((NestedAttribute)this).getNestedOptions(userRoleFunction, additionalTagFunction, additionalInputIdsFunction, combineTagFunction, perAttr, loadChildren, idToTagMap);
            } else {
                childTag = getOptionsTag(userRoleFunction, loadChildren, idToTagMap);
            }
            ContainerTag additionalTag = additionalTagFunction!=null && perAttr && !(this instanceof AbstractChartAttribute) ? additionalTagFunction.apply(getFullName()) : null;
            if(additionalTag!=null) {
                childTag = combineTagFunction.apply(additionalTag,(ContainerTag)childTag);
            }
            if(additionalInputIdsFunction!=null && !(this instanceof AbstractChartAttribute)) {
                List<String> additional = additionalInputIdsFunction.apply(getFullName());
                if(additional!=null) {
                    inputIds.addAll(additional);
                }
            }
        }
        String id = ((NestedAttribute)formParent).getId();
        String attrName = getFullName();
        String humanName = SimilarPatentServer.humanAttributeFor(attrName);
        if(inputIds.isEmpty()) inputIds = null;
        return div().attr("style", styleString).with(
                SimilarPatentServer.createAttributeElement(humanName, attrName,null,collapseId,childTag, id, getAttributeId(), inputIds, isNotYetImplemented(), getDescription().render())
        );
    }

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

    private String createSimpleNameText(String name) {
        if(name.contains(".")) {
            String[] split = name.split("\\.");
            return createSimpleNameText(split[split.length-1])+" of "+theOrAnd(split[0])+" "+singularize(createSimpleNameText(split[split.length-2]));
        } else {
            return SimilarPatentServer.humanAttributeFor(name).toLowerCase();
        }
    }

    private static String theOrAnd(String rootName) {
        if(Constants.NESTED_ATTRIBUTES.contains(rootName)|| Attributes.NESTED_ATTRIBUTES.contains(rootName)) {
            if(Arrays.asList("a","e","i","o","u","h").contains(rootName.substring(0,1))) {
                return "an";
            } else {
                return "a";
            }
        }
        else return "the";
    }

    protected static String singularize(String name) {
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
                    filter = new AbstractSimilarityGreaterThanFilter(this, filterType);
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
                case Regexp: {
                    filter = new RegexpTextFilter(this, filterType);
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
                case DoesNotExist: {
                    filter = new AbstractDoesNotExistFilter(this,filterType);
                    break;
                }
                case Exists: {
                    filter = new AbstractExistsFilter(this,filterType);
                    break;
                }
                case Nested: {
                    if(!(this instanceof NestedAttribute)) throw new RuntimeException("Only nested attributes support nested filterType");
                    filter = new AbstractNestedFilter((NestedAttribute)this);
                    break;
                }
                case IncludeWithRelated: {
                    filter = new AbstractIncludeWithRelatedFilter(this,filterType,getFieldType(),null);
                    break;
                }
                case ExcludeWithRelated: {
                    filter = new AbstractExcludeWithRelatedFilter(this,filterType,getFieldType(),null);
                    break;
                }
                case AssetExclude: {
                    filter = new AbstractExcludeAssetFilter(this,filterType,getFieldType(),null);
                    break;
                }
                case AssetInclude: {
                    filter = new AbstractIncludeAssetFilter(this,filterType,getFieldType(),null);
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
