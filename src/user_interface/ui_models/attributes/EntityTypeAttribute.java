package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;

import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/17/2017.
 */
public class EntityTypeAttribute extends AbstractAttribute<String> {

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio==null || portfolio.isEmpty()) return "";

        String item = portfolio.stream().findAny().get();
        if(Database.isApplication(item)) {
            return Database.entityTypeForApplication(item);
        } else if (Database.isAssignee(item)) {
            return Database.assigneeEntityType(item);
        } else {
            return Database.entityTypeForPatent(item);
        }
    }

    @Override
    public String getName() {
        return Constants.ASSIGNEE_ENTITY_TYPE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public String getType() {
        return "keyword";
    }
}
