package user_interface.ui_models.portfolios.attributes;

import seeding.ai_db_updater.tools.RelatedAssetsGraph;
import j2html.tags.Tag;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class FamilyMembersAttribute implements AbstractAttribute<String> {
    private static RelatedAssetsGraph graph = RelatedAssetsGraph.get();

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return "";
        String patent = portfolio.stream().findAny().get();
        return graph.relativesOf(patent);
    }

    @Override
    public String getName() {
        return Constants.PATENT_FAMILY;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
