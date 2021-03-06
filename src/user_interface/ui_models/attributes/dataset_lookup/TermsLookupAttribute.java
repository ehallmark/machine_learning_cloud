package user_interface.ui_models.attributes.dataset_lookup;

import j2html.tags.Tag;
import org.elasticsearch.script.Script;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.attributes.tools.AjaxMultiselect;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by Evan on 12/23/2017.
 */
public abstract class TermsLookupAttribute extends AbstractAttribute implements DependentAttribute {
    public TermsLookupAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }

    public abstract String getTermsIndex();
    public abstract String getTermsType();
    public abstract String getTermsPath();
    public abstract String getTermsName();

    public abstract List<String> termsFor(String asset);

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }

}
