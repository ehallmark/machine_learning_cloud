package user_interface.ui_models.portfolios.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 7/20/17.
 */
public class PatentTermAdjustmentAttribute implements AbstractAttribute<Integer> {

    @Override
    public Integer attributesFor(Collection<String> portfolio, int limit) {
        String item = portfolio.stream().findAny().get();
        return Database.termAdjustmentFor(item);
    }

    @Override
    public String getName() {
        return Constants.PATENT_TERM_ADJUSTMENT;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
