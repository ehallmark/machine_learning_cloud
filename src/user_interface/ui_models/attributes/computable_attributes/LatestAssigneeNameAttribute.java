package user_interface.ui_models.attributes.computable_attributes;

import lombok.NonNull;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class LatestAssigneeNameAttribute extends ComputableAssigneeAttribute<String> implements AjaxMultiselect {
    public LatestAssigneeNameAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.AdvancedKeyword, AbstractFilter.FilterType.Regexp,AbstractFilter.FilterType.Include,AbstractFilter.FilterType.Exclude));
    }
    @Override
    public String getType() {
        return "text";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE;
    }

    @Override
    public Map<String,Object> getNestedFields() {
        Map<String,Object> fields = new HashMap<>();
        Map<String,String> rawType = new HashMap<>();
        rawType.put("type","keyword");
        fields.put("raw",rawType);
        return fields;
    }

    @Override
    public String ajaxUrl() {
        return Constants.ASSIGNEE_NAME_AJAX_URL;
    }

    @Override
    protected String attributesForAssigneeHelper(@NonNull String assignee) {
        return assignee;
    }
}
