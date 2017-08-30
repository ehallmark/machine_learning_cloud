package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

/**
 * Created by ehallmark on 6/15/17.
 */
public class IndependentClaimAttribute extends AbstractScriptAttribute {
    public IndependentClaimAttribute() {
        super(Collections.emptyList());
    }
    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

    @Override
    public String getName() {
        return Constants.INDEPENDENT_CLAIM;
    }

    @Override
    public Script getScript() {
        return new Script(ScriptType.INLINE, "expression", "doc['"+Constants.CLAIMS+"."+Constants.PARENT_CLAIM_NUM+"'].empty", new HashMap<>());
    }
}
