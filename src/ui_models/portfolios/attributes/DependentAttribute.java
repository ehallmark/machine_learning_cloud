package ui_models.portfolios.attributes;

import j2html.tags.Tag;
import spark.Request;

import java.util.Collection;

/**
 * Created by Evan on 6/17/2017.
 */
public interface DependentAttribute {
    Collection<String> getPrerequisites();
    void extractRelevantInformationFromParams(Request params);
    Tag getOptionsTag();
}
