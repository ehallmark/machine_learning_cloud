package ui_models.portfolios.attributes;

import seeding.Constants;
import seeding.Database;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;

/**
 * Created by ehallmark on 6/15/17.
 */
public class AssigneeNameAttribute implements AbstractAttribute<String> {

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return "";
        return String.join("; ",Database.assigneesFor(portfolio.stream().findAny().get()));
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE;
    }
}
