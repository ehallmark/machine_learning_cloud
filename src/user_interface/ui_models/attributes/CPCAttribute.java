package user_interface.ui_models.attributes;

import seeding.Constants;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;

/**
 * Created by ehallmark on 6/15/17.
 */
public class CPCAttribute extends AbstractAttribute implements AjaxMultiselect {
    public CPCAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.PrefixExclude,AbstractFilter.FilterType.PrefixInclude, AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }

    @Override
    public String getName() {
        return Constants.CPC_CODES;
    }


    @Override
    public String getType() {
        return "keyword";
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
