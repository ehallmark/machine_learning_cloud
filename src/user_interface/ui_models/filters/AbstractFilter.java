package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;

import java.util.*;
import java.util.function.Function;

import static j2html.TagCreator.div;
import static j2html.TagCreator.span;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractFilter extends AbstractAttribute implements DependentAttribute<AbstractFilter> {


    public enum FilterType {
        Include, Exclude, GreaterThan, LessThan, BoolTrue, BoolFalse, Between, Nested, AdvancedKeyword, PrefixExclude, PrefixInclude, Regexp, Exists, DoesNotExist, AssetInclude, AssetExclude, IncludeWithRelated, ExcludeWithRelated
    }

    public static boolean isPrefix(FilterType type) {
        if(Arrays.asList(FilterType.Include,FilterType.Exclude,FilterType.IncludeWithRelated, FilterType.ExcludeWithRelated, FilterType.AssetExclude, FilterType.AssetInclude, FilterType.BoolTrue,FilterType.BoolFalse).contains(type)) return true;
        return false;
    }

    public enum FieldType {
        Text, Multiselect, Select, Integer, Double, Date, Boolean, NestedObject, Object
    }

    @Getter @Setter
    protected AbstractAttribute attribute;
    @Getter
    protected FilterType filterType;
    @Setter @Getter
    protected AbstractFilter parent;
    protected boolean isScriptFilter;
    @Getter
    protected Map<String,Collection<String>> synonymMap;
    public AbstractFilter(AbstractAttribute attribute, FilterType filterType) {
        super(Collections.singleton(filterType));
        this.attribute=attribute!=null && attribute instanceof DependentAttribute ? ((DependentAttribute) attribute).dup() : attribute;
        this.filterType=filterType;
        this.isScriptFilter =attribute!=null &&  attribute instanceof AbstractScriptAttribute;
    }

    public String getAttributeId() {
        return null;
    }

    public String getPrerequisite() {
        return attribute.getName();
    }

    public boolean isActive() { return true; }

    public abstract QueryBuilder getFilterQuery();

    @Override
    public AbstractFilter clone() {
        return dup();
    }

    public boolean contributesToScore() { return false; }

    public String getFullPrerequisite() {
         return (parent==null?"":(parent.getFullPrerequisite()+"."))+getPrerequisite();
    }

    @Override
    public abstract Tag getOptionsTag(Function<String,Boolean> userRoleFunction, boolean loadChildren, Map<String,String> idToTagMap);

    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction, Function<String,Tag> additionalTagFunction, Function<String,List<String>> additionalInputIdsFunction, boolean loadChildren, Map<String,String> idToTagMap) {
        String collapseId = "collapse-filters-"+getName().replaceAll("[\\[\\]]","");
        String styleString = "display: none; margin-left: 5%; margin-right: 5%;";
        Tag childTag;
        List<String> inputIds = new ArrayList<>();
        if(getInputIds()!=null) {
            inputIds.addAll(getInputIds());
        }
        if(this instanceof AbstractNestedFilter && filterType.equals(FilterType.Nested)) {
            childTag = ((AbstractNestedFilter) this).getNestedOptions(userRoleFunction,additionalTagFunction,additionalInputIdsFunction,loadChildren, idToTagMap);
        } else {
            childTag = getOptionsTag(userRoleFunction,loadChildren, idToTagMap);
            Tag additionalTag = additionalTagFunction!=null ? additionalTagFunction.apply(getName()) : null;
            if(additionalTag!=null) {
                childTag = div().with(additionalTag,childTag);
            }
            if(additionalInputIdsFunction!=null) {
                List<String> additional = additionalInputIdsFunction.apply(getName());
                if(additional!=null) {
                    inputIds.addAll(additional);
                }
            }
        }

        String id = ((AbstractNestedFilter)formParent).getId();
        if(inputIds.isEmpty()) inputIds = null;
        return div().attr("style", styleString).with(
                SimilarPatentServer.createAttributeElement(SimilarPatentServer.humanAttributeFor(getName()),getName(),getOptionGroup(),collapseId,childTag,id, getAttributeId(), inputIds, isNotYetImplemented(), getDescription().render())
        );
    }

    @Override
    public Collection<String> getAllValues() {
        return attribute.getAllValues();
    }

    @Override
    public String getType() {
        return attribute.getType();
    }

    public String getId() {
        return getName().replaceAll("[\\[\\]]","")+filterType.toString();
    }

    @Override
    public String getName() {
        if(parent==null) {
            return getPrerequisite()+filterType.toString()+Constants.FILTER_SUFFIX;
        } else {
            // nested form field
            return parent.getName().replaceAll("[\\[\\]]","")+getPrerequisite()+filterType.toString()+Constants.FILTER_SUFFIX.replaceAll("[\\[\\]\\]]","")+"[]";
        }
    }

    @Override
    public FieldType getFieldType() { return attribute.getFieldType(); }

    public QueryBuilder getScriptFilter() {
        Script filterScript = getScript();
        if(filterScript==null)  return QueryBuilders.boolQuery();
        return QueryBuilders.scriptQuery(filterScript);
    }


    public Script getScript() {
        if(attribute!=null && !(attribute instanceof AbstractScriptAttribute)) throw new RuntimeException("Getting script filter for non script attribute: "+attribute.getName());
        AbstractScriptAttribute scriptAttribute = (AbstractScriptAttribute)attribute;
        Script searchScript = scriptAttribute.getScript(true,false);
        if(searchScript==null) return null;
        String transformedScript = transformAttributeScript(searchScript.getIdOrCode());
        System.out.println("Transformed script for: "+attribute.getFullName()+": "+transformedScript);
        Script filterScript = new Script(
                searchScript.getType(),
                searchScript.getLang(),
                transformedScript,
                searchScript.getParams()
        );
        return filterScript;
    }

    protected abstract String transformAttributeScript(String attributeScript);

    @Override
    public boolean isNotYetImplemented() {
        return attribute!=null && attribute.isNotYetImplemented();
    }

    @Override
    public Tag getDescription() {
        return span().with(getDescriptionFor(getFilterType()),attribute.getDescription(this));
    }

    public String getOptionGroup() {
        String optGroup = getFullPrerequisite();
        if(optGroup.endsWith(Constants.COUNT_SUFFIX)) {
            optGroup = optGroup.substring(0,optGroup.length()-Constants.COUNT_SUFFIX.length());
        }
        return optGroup;
    }

    @Override
    public List<String> getInputIds() {
        return Collections.singletonList(getId());
    }

    protected Tag getDescriptionFor(FilterType filterType) {
        String description;
        String scope = attribute.getScope() != null ? attribute.getScope() : "results";
        switch(filterType) {
            case Exclude: {
                description = "This filter excludes "+scope+" that match";
                break;
            }
            case Include: {
                description = "This filter only includes "+scope+" that match";
                break;
            }
            case AdvancedKeyword: {
                description = "This filter only includes "+scope+" that match";
                break;
            }
            case Regexp: {
                description = "This filter only includes "+scope+" that match";
                break;
            }
            case Between: {
                description = "This filter only includes "+scope+" between";
                break;
            }
            case GreaterThan: {
                description = "This filter only includes "+scope+" greater than";
                break;
            }
            case LessThan: {
                description = "This filter only includes "+scope+" less than";
                break;
            }
            case PrefixExclude: {
                description = "This filter only includes "+scope+" starting with";
                break;
            }
            case PrefixInclude: {
                description = "This filter excludes "+scope+" starting with";
                break;
            }
            case Nested: {
                description = "This nested filter contains the following values to filter:";
                break;
            }
            case BoolTrue: {
                description = "This filter only includes "+scope+" that are";
                break;
            }
            case BoolFalse: {
                description = "This filter excludes "+scope+" that are";
                break;
            }
            case Exists: {
                description = "This filter removes "+scope+" without a value for";
                break;
            }
            case DoesNotExist: {
                description = "This filter removes "+scope+" with a value for";
                break;
            }
            case ExcludeWithRelated: {
                description = "This filter only includes " + scope + " that match a related asset of";
                break;
            }
            case IncludeWithRelated: {
                description = "This filter excludes "+scope+" that match a related asset of";
                break;
            }
            case AssetExclude: {
                description = "This filter excludes "+scope+" that match";
                break;
            }
            case AssetInclude: {
                description = "This filter only includes "+scope+" that match";
                break;
            }
            default: {
                description = "";
                break;
            }
        };
        return span(description);
    }
}
