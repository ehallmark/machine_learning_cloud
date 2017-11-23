package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import lombok.NonNull;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class CPCAttribute extends ComputableCPCAttribute<List<String>> implements AjaxMultiselect {
    public CPCAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
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

    @Override
    protected List<String> attributesforCPCsHelper(@NonNull Collection<String> cpcs) {
        return new ArrayList<>(cpcs);
    }
}
