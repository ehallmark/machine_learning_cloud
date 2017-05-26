package ui_models.attributes.classification;

import org.deeplearning4j.berkeley.Pair;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.List;

/**
 * Created by Evan on 5/9/2017.
 */
public interface ClassificationAttr extends AbstractAttribute<List<Pair<String,Double>>> {

    int numClassifications();
    Collection<String> getClassifications();
}
