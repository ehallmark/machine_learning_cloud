package ui_models.portfolios.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class PortfolioSizeAttribute implements AbstractAttribute<Integer> {

    @Override
    public Integer attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return 0;
        String item = portfolio.stream().findAny().get();
        if(Database.isAssignee(item)) return Database.getAssetCountFor(item);
        else return Database.assigneesFor(item).stream().collect(Collectors.summingInt(assignee->{
            return Database.getAssetCountFor(assignee);
        }));
    }

    @Override
    public String getName() {
        return Constants.PORTFOLIO_SIZE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
