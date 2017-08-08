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
public class RemainingLifeAttribute extends AbstractAttribute<Integer> {

    @Override
    public Integer attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return 0;
        String item = portfolio.stream().findAny().get();
        return Database.getLifeRemainingMap().getOrDefault(item,0);
    }

    @Override
    public String getName() {
        return Constants.REMAINING_LIFE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public String getType() {
        return "integer";
    }
}
