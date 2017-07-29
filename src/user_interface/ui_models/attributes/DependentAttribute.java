package user_interface.ui_models.attributes;

import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;

/**
 * Created by Evan on 6/17/2017.
 */
public interface DependentAttribute<T> extends AbstractAttribute<T> {
    Collection<String> getPrerequisites();
    void extractRelevantInformationFromParams(Request params);
}
