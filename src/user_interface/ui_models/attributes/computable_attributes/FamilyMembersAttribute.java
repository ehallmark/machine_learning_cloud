package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.ai_db_updater.tools.RelatedAssetsGraph;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

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
    public String[] attributesFor(Collection<String> items, int limit) {
        RelatedAssetsGraph graph = RelatedAssetsGraph.get();
        if(items.isEmpty()) return null;
        Collection<String> relatives = graph.relatives(items.stream().findAny().get());
        return relatives.toArray(new String[relatives.size()]);
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Text;
    }

}
