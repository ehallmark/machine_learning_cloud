package user_interface.ui_models.attributes.script_attributes;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
    public Script getScript(boolean requireFilter, boolean idOnly) {
        if(idOnly) {
            return new Script(ScriptType.STORED,language,getFullName(),getParams());
        }
        String script = createScriptFor(type,language,fieldName);
        System.out.println("Script: "+script);
        return new Script(ScriptType.INLINE,language,script, getParams());
    }

    @Override
    public Map<String,Object> getParams() {
        Map<String,Object> params = new HashMap<>();
        params.put("defaultVal",defaultVal);
        return params;
    }

    private String createScriptFor(String type, String language, String field) {
        return "("+emptyDocFieldCheck(language, field)+" ? ("+paramsFieldForLanguage(language,"defaultVal")+") : (doc['"+field+"']."+type+"))";
    }

    private static String paramsFieldForLanguage(String language, String param) {
        if(language.equals("expression")) {
            return param;
        } else {
            return "params."+param;
        }
    }

    private static String emptyDocFieldCheck(String language, String name) {
        if(language.equals("expression")) {
            return "doc['"+name+"'].empty";
        } else {
            return "doc.containsKey('"+name+"')";
        }
    }
}
