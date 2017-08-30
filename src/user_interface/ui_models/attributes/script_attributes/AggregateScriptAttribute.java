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
    enum AggregationType {
        value,
        empty,
        length,
        min_,
        max_,
        avg_,
        median_,
        sum_
    }

    private AggregationType type;
    private String defaultVal;
    protected String fieldName;
    public AggregateScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes, String fieldName, String defaultVal, AggregationType type) {
        super(filterTypes);
        this.type=type;
        this.defaultVal=defaultVal;
        this.fieldName = fieldName;
    }

    @Override
    public Script getScript() {
        String script = "doc['"+fieldName+"'].empty ? ("+defaultVal+") : (doc['"+fieldName+"']."+type()+")";
        return new Script(ScriptType.INLINE,"expression",script, new HashMap<>());
    }

    private String type() {
        return type.toString().endsWith("_") ? type.toString().substring(0,type.toString().length()-1)+"()" : type.toString();
    }

}
