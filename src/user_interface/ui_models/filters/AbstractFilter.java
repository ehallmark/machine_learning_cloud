package user_interface.ui_models.filters;

import org.elasticsearch.index.query.QueryBuilder;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractFilter implements DependentAttribute {
    @Override
    public Object attributesFor(Collection portfolio, int limit) {
        throw new UnsupportedOperationException("Filter shouldn't use attributesFor method.");
    }

    public abstract boolean shouldKeepItem(Item obj);

    @Override
    public Collection<String> getPrerequisites() {
        return Arrays.asList(getPrerequisite());
    }

    public abstract String getPrerequisite();

    public boolean isActive() { return true; }

    public abstract QueryBuilder getFilterQuery();
}
