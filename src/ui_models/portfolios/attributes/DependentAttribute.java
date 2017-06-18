package ui_models.portfolios.attributes;

import java.util.Collection;

/**
 * Created by Evan on 6/17/2017.
 */
public interface DependentAttribute {
    Collection<String> getPrerequisites();
}
