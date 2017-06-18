package ui_models.filters;

import j2html.tags.Tag;
import spark.Request;
import ui_models.attributes.AbstractAttribute;
import ui_models.portfolios.attributes.DependentAttribute;
import ui_models.portfolios.items.Item;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractFilter implements DependentAttribute {
    enum Type {
        PreFilter, PostFilter
    }
    public abstract void extractRelevantInformationFromParams(Request params);

    public abstract Tag getOptionsTag();

    public boolean defaultSelected() {
        return false;
    }

    public abstract boolean shouldKeepItem(Item obj);

    @Override
    public Collection<String> getPrerequisites() {
        return Collections.emptyList();
    }
}
