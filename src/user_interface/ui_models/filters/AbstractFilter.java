package user_interface.ui_models.filters;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;

import static j2html.TagCreator.div;
import static j2html.TagCreator.p;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractFilter extends AbstractAttribute implements DependentAttribute<AbstractFilter> {

    public enum FilterType {
        Include, Exclude, GreaterThan, LessThan, BoolTrue, BoolFalse, Between, Nested, AdvancedKeyword, PrefixExclude, PrefixInclude
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

    public QueryBuilder getScriptFilter() {
        if(!(attribute instanceof AbstractScriptAttribute)) throw new RuntimeException("Getting script filter for non script attribute: "+attribute.getName());
        AbstractScriptAttribute scriptAttribute = (AbstractScriptAttribute)attribute;
        Script searchScript = scriptAttribute.getScript();
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
        return div().with(getDescriptionFor(getFilterType()),attribute.getDescription(true));
    }


    protected static Tag getDescriptionFor(FilterType filterType) {
        String description;
        switch(filterType) {
            case Exclude: {
                description = "This filter excludes results that match";
                break;
            }
            case Include: {
                description = "This filter only includes results that match";
                break;
            }
            case AdvancedKeyword: {
                description = "This filter only includes results that match";
                break;
            }
            case Between: {
                description = "This filter only includes results between";
                break;
            }
            case GreaterThan: {
                description = "This filter only includes results greater than";
                break;
            }
            case LessThan: {
                description = "This filter only includes results less than";
                break;
            }
            case PrefixExclude: {
                description = "This filter only includes results starting with";
                break;
            }
            case PrefixInclude: {
                description = "This filter excludes results starting with";
                break;
            }
            case Nested: {
                description = "This nested filter contains the following values to filter";
                break;
            }
            case BoolTrue: {
                description = "This filter only includes results that are";
                break;
            }
            case BoolFalse: {
                description = "This filter excludes results that are";
                break;
            }
            default: {
                description = "";
                break;
            }
        };
        return div().with(
                div().withText(description)
        );
    }
}
