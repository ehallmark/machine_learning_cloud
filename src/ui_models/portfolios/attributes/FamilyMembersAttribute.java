package ui_models.portfolios.attributes;

import graphical_models.related_docs.RelatedAssetsGraph;
import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.Collections;

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
