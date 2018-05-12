package seeding.google.elasticsearch.attributes;

import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AssigneeName extends KeywordAndTextAttribute implements AjaxMultiselect {
    @Override
    public String getName() {
        return Attributes.ASSIGNEE_HARMONIZED;
    }

    @Override
    public String ajaxUrl() {
        return Constants.ASSIGNEE_NAME_AJAX_URL;
    }
}
