package user_interface.ui_models.attributes;

import seeding.ai_db_updater.tools.RelatedAssetsGraph;
import j2html.tags.Tag;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class FamilyMembersAttribute extends ComputableAttribute<String[]> {
    private static RelatedAssetsGraph graph = RelatedAssetsGraph.get();
    public FamilyMembersAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
    }

    @Override
    public String[] attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return new String[]{};
        String patent = portfolio.stream().findAny().get();
        Collection<String> relatives = graph.relatives(patent);
        return relatives.toArray(new String[relatives.size()]);
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
