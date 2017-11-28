package user_interface.ui_models.attributes.computable_attributes;

import lombok.NonNull;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToNormalizedAssigneeMap;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 6/15/17.
 */
public class NormalizedAssigneeNameAttribute extends ComputableNormalizedAssigneeAttribute<String> implements AjaxMultiselect {
    public NormalizedAssigneeNameAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.AdvancedKeyword, AbstractFilter.FilterType.Regexp, AbstractFilter.FilterType.Include,AbstractFilter.FilterType.Exclude));
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
        return Constants.NORMALIZED_LATEST_ASSIGNEE;
    }

    @Override
    protected String attributesForAssigneeHelper(@NonNull String assignee) {
        return assignee;
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
        return Constants.NORMALIZED_ASSIGNEE_NAME_AJAX_URL;
    }
}
