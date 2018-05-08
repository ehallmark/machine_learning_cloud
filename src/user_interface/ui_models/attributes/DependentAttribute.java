package user_interface.ui_models.attributes;

import spark.Request;

/**
 * Created by Evan on 6/17/2017.
 */
public interface DependentAttribute<T extends AbstractAttribute> {
    void extractRelevantInformationFromParams(Request params);
    T dup();
}
