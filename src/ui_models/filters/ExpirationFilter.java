package ui_models.filters;

import seeding.Database;
import ui_models.portfolios.items.AbstractPatent;
import ui_models.portfolios.items.Item;

/**
 * Created by Evan on 6/13/2017.
 */
public class ExpirationFilter implements AbstractFilter {
    @Override
    public boolean shouldKeepItem(Item obj) {
        if (obj instanceof AbstractPatent) {
            return Database.isExpired(obj.getName());
        } else {
            return true;
        }
    }
}
