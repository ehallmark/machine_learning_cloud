package user_interface.ui_models.attributes.script_attributes;

import lombok.Getter;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public abstract class AggregateScriptAttribute extends AbstractScriptAttribute {
    private static final Map<String,String> scriptMap = Collections.synchronizedMap(new HashMap<>());


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
        String script = getOrCreateScriptFor(type,language);
        Map<String,Object> params = new HashMap<>();
        params.put("defaultVal",defaultVal);
        params.put("field",fieldName);
        return new Script(ScriptType.INLINE,language,script, params);
    }

    private String getOrCreateScriptFor(String type, String language) {
        synchronized (scriptMap) {
            if (scriptMap.containsKey(type+"_"+language)) {
            } else {
                String scriptStr = "("+emptyDocFieldCheck(language)+" ? ("+paramsFieldForLanguage(language,"defaultVal")+") : (doc["+paramsFieldForLanguage(language,"field")+")]."+type+"))";
                scriptMap.put(type+"_"+language,scriptStr);
            }
            return scriptMap.get(type);
        }
    }

    private static String paramsFieldForLanguage(String language, String param) {
        if(language.equals("expression")) {
            return param;
        } else {
            return "params."+param;
        }
    }

    private static String emptyDocFieldCheck(String language) {
        String name = paramsFieldForLanguage(language,"field");
        if(language.equals("expression")) {
            return "doc["+name+")].empty";
        } else {
            return "doc.containsKey("+name+")";
        }
    }
}
