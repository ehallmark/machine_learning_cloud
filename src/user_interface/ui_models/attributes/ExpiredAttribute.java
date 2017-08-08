package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 7/20/17.
 */
public class ExpiredAttribute extends AbstractAttribute<Boolean> {

    @Override
    public Boolean attributesFor(Collection<String> portfolio, int limit) {
        String item = portfolio.stream().findAny().get();
        return Database.isExpired(item);
    }

    @Override
    public String getName() {
        return Constants.EXPIRED;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public String getType() {
        return "boolean";
    }
}
