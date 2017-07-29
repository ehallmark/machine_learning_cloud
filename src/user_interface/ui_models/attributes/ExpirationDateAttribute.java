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
public class ExpirationDateAttribute implements AbstractAttribute<String> {

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        String item = portfolio.stream().findAny().get();
        return Database.expirationDateFor(item);
    }

    @Override
    public String getName() {
        return Constants.EXPIRATION_DATE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
