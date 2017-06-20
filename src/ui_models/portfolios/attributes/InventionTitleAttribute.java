package ui_models.portfolios.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class InventionTitleAttribute implements AbstractAttribute<String> {

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return "";
        return Database.getInventionTitleFor(portfolio.stream().findAny().get());
    }

    @Override
    public String getName() {
        return Constants.INVENTION_TITLE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
