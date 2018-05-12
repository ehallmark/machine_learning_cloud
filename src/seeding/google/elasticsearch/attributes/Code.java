package seeding.google.elasticsearch.attributes;

import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;

public class Code extends KeywordWithPrefixAttribute implements AjaxMultiselect {
    @Override
    public String getName() {
        return Attributes.CODE;
    }

    @Override
    public String ajaxUrl() {
        return Constants.CPC_CODE_AJAX_URL;
    }
}
