package user_interface.ui_models.attributes.script_attributes;

import lombok.Getter;
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
    @Getter
    private Object defaultVal;
    protected String fieldName;
    private String language;
    public AggregateScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes, String language, String fieldName, Object defaultVal, String type) {
        super(filterTypes);
        this.type=type;
        this.defaultVal=defaultVal;
        this.fieldName = fieldName;
        this.language = language;
    }

    @Override
    public Script getScript() {
        String script = "("+emptyDocFieldCheck(fieldName,language)+" ? ("+defaultVal.toString()+") : (doc['"+fieldName+"']."+type+"))";
        return new Script(ScriptType.INLINE,language,script, new HashMap<>());
    }

    private static String emptyDocFieldCheck(String name, String language) {
        if(language.equals("expression")) {
            return "doc['"+name+"'].empty";
        } else {
            return "doc.containsKey('"+name+"')";
        }
    }
}
