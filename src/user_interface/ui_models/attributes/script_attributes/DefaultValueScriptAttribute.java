package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 * Created by ehallmark on 7/20/17.
 */
public abstract class DefaultValueScriptAttribute extends AbstractScriptAttribute {
    private String defaultVal;
    public DefaultValueScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes, String defaulVal) {
        super(filterTypes);
        this.defaultVal=defaulVal;
    }

    @Override
    public Script getScript() {
        String script = "doc['"+getName()+"'].empty ? ("+defaultVal+") : (doc['"+getName()+"'])";
        return new Script(ScriptType.INLINE,"expression",script, new HashMap<>());
    }

}
