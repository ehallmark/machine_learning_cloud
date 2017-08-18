package user_interface.ui_models.attributes;

import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by Evan on 6/17/2017.
 */
public interface DependentAttribute {
    void extractRelevantInformationFromParams(Request params);
}
