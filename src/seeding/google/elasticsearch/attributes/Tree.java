package seeding.google.elasticsearch.attributes;

import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;

public class Tree extends KeywordAttribute implements AjaxMultiselect {
    @Override
    public String getName() {
        return Attributes.TREE;
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

    @Override
    public String ajaxUrl() {
        return Constants.CPC_CODE_AJAX_URL;
    }


}
