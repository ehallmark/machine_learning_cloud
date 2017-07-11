package user_interface.ui_models.portfolios.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class AssigneeNameAttribute implements AbstractAttribute<String> {

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return "";
        String item = portfolio.stream().findAny().get();
        if(Database.isAssignee(item)) return item;
        else return String.join("; ",Database.assigneesFor(item));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
