package seeding.google.elasticsearch.attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class Score extends AbstractScriptAttribute implements ConvenienceAttribute {

    public Score() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public Map<String, Object> getParams() {
        return Collections.emptyMap();
    }

    @Override
    public Script getScript(boolean requireParams, boolean idOnly) {
        return new Script(ScriptType.INLINE,"expression","(_score==null) ? 0.0 : _score",Collections.emptyMap());
    }

    @Override
    public String getName() {
        return Constants.SCORE;
    }

    @Override
    public String getType() {
        return "double";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }
}
