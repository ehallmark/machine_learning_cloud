package user_interface.ui_models.attributes;

import j2html.tags.Tag;

import java.util.Collection;

/**
 * Created by Evan on 5/9/2017.
 */
public interface AbstractAttribute<T> {
    T attributesFor(Collection<String> portfolio, int limit);
    String getName();
    Tag getOptionsTag();
}
