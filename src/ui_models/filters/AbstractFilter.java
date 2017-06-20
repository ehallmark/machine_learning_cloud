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

    @Override
    public Object attributesFor(Collection portfolio, int limit) {
        throw new UnsupportedOperationException("Filter shouldn't use attributesFor method.");
    }

    public boolean defaultSelected() {
        return false;
    }

    public abstract boolean shouldKeepItem(Item obj);

    @Override
    public Collection<String> getPrerequisites() {
        return Collections.emptyList();
    }

    @Override
    public void extractRelevantInformationFromParams(Request params) {

    }
}
