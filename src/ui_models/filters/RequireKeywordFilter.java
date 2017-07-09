package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;

/**
 * Created by Evan on 7/9/2017.
 */
public class RequireKeywordFilter extends AbstractFilter {
    @Override
    public String getName() {
        return Constants.REQUIRE_KEYWORD_FILTER;
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                textarea().withClass("form-control").attr("placeholder","Enter keywords").withName(Constants.REQUIRE_KEYWORD_FILTER)
        );
    }
    @Override
    public boolean shouldKeepItem(Item obj) {
        return true;
    }
}
