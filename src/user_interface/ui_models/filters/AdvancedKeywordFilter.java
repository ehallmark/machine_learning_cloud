package user_interface.ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;

/**
 * Created by Evan on 7/9/2017.
 */
public class AdvancedKeywordFilter extends AbstractFilter {
    @Override
    public String getName() {
        return Constants.ADVANCED_KEYWORD_FILTER;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","Example: (( smart <-> home ) | ( home <-> automation )) & ! lighting").withName(Constants.ADVANCED_KEYWORD_FILTER)
        );
    }
    @Override
    public boolean shouldKeepItem(Item obj) {
        return true;
    }

    public boolean isActive() { return false; }
}
