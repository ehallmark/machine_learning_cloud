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
public class JapaneseAttribute implements AbstractAttribute<Boolean> {

    @Override
    public Boolean attributesFor(Collection<String> portfolio, int limit) {
        String item = portfolio.stream().findAny().get();
        return Database.isJapaneseAssignee(item);
    }

    @Override
    public String getName() {
        return Constants.JAPANESE_ASSIGNEE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
