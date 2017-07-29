package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import models.classification_models.ClassificationAttr;

import java.util.Collection;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyAttribute implements AbstractAttribute<String> {

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio==null || portfolio.isEmpty()) return "";

        String item = portfolio.stream().findAny().get();
        return Database.getItemToTechnologyMap().getOrDefault(item, "");
    }

    @Override
    public String getName() {
        return Constants.TECHNOLOGY;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
