package ui_models.attributes.classification;

import org.deeplearning4j.berkeley.Pair;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.List;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ClassificationAttr implements AbstractAttribute<List<Pair<String,Double>>> {
    protected double weight;
    public void setWeight(double weight) {
        this.weight=weight;
    }
    public double getWeight() { return weight; }
    public abstract int numClassifications();
    public abstract Collection<String> getClassifications();
}
