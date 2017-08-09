package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class StreamableAttribute<T> extends AbstractAttribute<T>{
    public StreamableAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    public T attributesFor(Collection<String> portfolio, int limit) {
        throw new UnsupportedOperationException("streamable attributes");
    }


}
