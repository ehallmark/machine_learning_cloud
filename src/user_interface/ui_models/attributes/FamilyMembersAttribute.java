package user_interface.ui_models.attributes;

import seeding.ai_db_updater.tools.RelatedAssetsGraph;
import j2html.tags.Tag;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class FamilyMembersAttribute extends ComputableAttribute<String[]> {
    public FamilyMembersAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }

    @Override
    public String getName() {
        return Constants.PATENT_FAMILY;
    }

    @Override
    public String getType() {
        return "keyword";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

}
