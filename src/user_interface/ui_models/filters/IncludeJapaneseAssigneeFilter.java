package user_interface.ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.portfolios.items.Item;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 5/10/17.
 */
public class IncludeJapaneseAssigneeFilter extends AbstractBooleanIncludeFilter {
    @Override
    public String getPrerequisite() {
        return Constants.JAPANESE_ASSIGNEE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {}

    @Override
    public String getName() {
        return Constants.JAPANESE_ONLY_FILTER;
    }

}
