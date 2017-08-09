package user_interface.ui_models.attributes;

import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by Evan on 6/17/2017.
 */
public abstract class DependentAttribute<T> extends AbstractAttribute<T> {
    public DependentAttribute() {
        super(Collections.emptyList());
    }

    public abstract Collection<String> getPrerequisites();
    public abstract void extractRelevantInformationFromParams(Request params);
}
