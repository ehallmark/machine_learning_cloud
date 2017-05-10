package ui_models.filters;

import ui_models.portfolios.items.AbstractAssignee;
import ui_models.portfolios.items.AbstractPatent;
import ui_models.portfolios.items.Item;

import java.util.Collection;

/**
 * Created by ehallmark on 5/10/17.
 */
public class LabelFilter implements AbstractFilter {
    private Collection<String> labelsToRemove;
    public LabelFilter(Collection<String> labelsToRemove) {
        this.labelsToRemove=labelsToRemove;
    }
    @Override
    public boolean shouldKeepItem(Item item) {
        return !labelsToRemove.contains(item.getName());
    }
}
