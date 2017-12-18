package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.Setter;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static j2html.TagCreator.span;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractFilter extends AbstractAttribute implements DependentAttribute<AbstractFilter> {

    public enum FilterType {
        Include, Exclude, GreaterThan, LessThan, BoolTrue, BoolFalse, Between, Nested, AdvancedKeyword, PrefixExclude, PrefixInclude, Regexp, Exists, DoesNotExist
    }

    public static boolean isPrefix(FilterType type) {
        if(Arrays.asList(FilterType.Include,FilterType.Exclude,FilterType.BoolTrue,FilterType.BoolFalse).contains(type)) return true;
        return false;
    }

    public enum FieldType {
        Text, Multiselect, Select, Integer, Double, Date, Boolean, NestedObject, Object
    }

    @Getter
    protected AbstractAttribute attribute;
    @Getter
    protected FilterType filterType;
    @Setter @Getter
    protected AbstractFilter parent;
    protected boolean isScriptFilter;
    public AbstractFilter(AbstractAttribute attribute, FilterType filterType) {
        super(Arrays.asList(filterType));
        this.attribute=attribute instanceof DependentAttribute ? ((DependentAttribute) attribute).dup() : attribute;
        this.filterType=filterType;
        this.isScriptFilter = attribute instanceof AbstractScriptAttribute;
    }

    public String getAttributeId() {
        return null;
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
    public abstract Tag getOptionsTag(Function<String,Boolean> userRoleFunction);

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
        if(!(attribute instanceof AbstractScriptAttribute)) throw new RuntimeException("Getting script filter for non script attribute: "+attribute.getName());
        AbstractScriptAttribute scriptAttribute = (AbstractScriptAttribute)attribute;
        Script searchScript = scriptAttribute.getScript();
        if(searchScript==null) return QueryBuilders.boolQuery();
        Script filterScript = new Script(
                searchScript.getType(),
                searchScript.getLang(),
                transformAttributeScript(searchScript.getIdOrCode()),
                searchScript.getParams()
        );
        return QueryBuilders.scriptQuery(filterScript);
    }

    protected abstract String transformAttributeScript(String attributeScript);

    @Override
    public boolean isNotYetImplemented() {
        return attribute.isNotYetImplemented();
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
            default: {
                description = "";
                break;
            }
        };
        return span(description);
    }
}
