package ui_models.filters;

import seeding.Database;
import ui_models.portfolios.items.AbstractAssignee;
import ui_models.portfolios.items.AbstractPatent;
import ui_models.portfolios.items.Item;

/**
 * Created by ehallmark on 5/10/17.
 */
public class PortfolioSizeFilter implements AbstractFilter {
    private int limit;
    public PortfolioSizeFilter(int limit) { this.limit=limit; }
    @Override
    public boolean shouldKeepItem(Item obj) {
        if(limit <= 0) return true;

        if(obj instanceof AbstractPatent) {
            return Database.getAssetCountFor(((AbstractPatent)obj).getAssignee()) <= limit;
        } else if (obj instanceof AbstractAssignee) {
            return Database.getAssetCountFor(obj.getName()) <= limit;
        } else {
            return Database.selectPatentNumbersFromClassAndSubclassCodes(obj.getName()).size() <= limit;
        }
    }
}
