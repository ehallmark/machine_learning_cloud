package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class AssigneeNameAttribute extends AbstractAttribute<String[]> {
    @Override
    public String getType() {
        return "text";
    }

    @Override
    public String[] attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return new String[]{};
        String item = portfolio.stream().findAny().get();
        if(Database.isAssignee(item)) return new String[]{item};
        else {
            Collection<String> assignees = Database.assigneesFor(item);
            return assignees.toArray(new String[assignees.size()]);
        }
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
