package ui_models.filters;

import ui_models.portfolios.items.AbstractAssignee;
import ui_models.portfolios.items.AbstractPatent;
import ui_models.portfolios.items.Item;

import java.util.Collection;

/**
 * Created by ehallmark on 5/10/17.
 */
public class AssigneeFilter implements AbstractFilter {
    private Collection<String> assigneesToRemove;
    public AssigneeFilter(Collection<String> assigneesToRemove) {
        this.assigneesToRemove=assigneesToRemove;
    }
    @Override
    public boolean shouldKeepItem(Item item) {
        if(item instanceof AbstractAssignee) {
            return !assigneesToRemove.contains(item.getName());
        } else if(item instanceof AbstractPatent) {
            return !assigneesToRemove.contains(((AbstractPatent) item).getAssignee());
        } else {
            return false;
        }
    }
}
