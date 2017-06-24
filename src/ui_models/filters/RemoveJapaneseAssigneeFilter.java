package ui_models.filters;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import static j2html.TagCreator.div;
import static j2html.TagCreator.textarea;

/**
 * Created by ehallmark on 5/10/17.
 */
public class RemoveJapaneseAssigneeFilter extends AssigneeFilter {

    public RemoveJapaneseAssigneeFilter() {
        super(Database.getJapaneseCompanies());
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {}

    @Override
    public String getName() {
        return Constants.NO_JAPANESE_FILTER;
    }
}
