package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.HashMap;

/**
 * Created by ehallmark on 7/20/17.
 */
public abstract class AggregateScriptAttribute extends AbstractScriptAttribute {


    private String type;
    private String defaultVal;
    protected String fieldName;
    private String language;
    public AggregateScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes, String language, String fieldName, String defaultVal, String type) {
        super(filterTypes);
        this.type=type;
        this.defaultVal=defaultVal;
        this.fieldName = fieldName;
        this.language = language;
    }

    public AggregateScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes, String fieldName, String defaultVal, String type) {
        this(filterTypes, "expression", fieldName, defaultVal, type);
    }

    @Override
    public Script getScript() {
        String script = "doc['"+fieldName+"'].empty ? ("+defaultVal+") : (doc['"+fieldName+"']."+type+")";
        return new Script(ScriptType.INLINE,language,script, new HashMap<>());
    }

}
