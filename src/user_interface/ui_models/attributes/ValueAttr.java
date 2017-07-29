package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import lombok.Getter;
import user_interface.ui_models.attributes.AbstractAttribute;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import models.value_models.*;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ValueAttr implements AbstractAttribute<Double> {
    // Instance class
    @Getter
    protected Map<String,Double> model;
    @Getter
    protected String name;
    protected ValueMapNormalizer.DistributionType distributionType;
    private static final double DEFAULT_VAL = (ValueMapNormalizer.DEFAULT_START+ValueMapNormalizer.DEFAULT_END)/2d;
    protected double defaultVal;

    public ValueAttr(ValueMapNormalizer.DistributionType distributionType, String modelName, double defaultVal, boolean loadData) {
        this.name=modelName;
        this.defaultVal=defaultVal;
        this.distributionType=distributionType;
        if(loadData)setModel();
    }

    public ValueAttr(ValueMapNormalizer.DistributionType distributionType, String modelName, boolean loadData) {
        this(distributionType,modelName,DEFAULT_VAL, loadData);
    }

    public void setModel() {
        model=new ValueMapNormalizer(distributionType).normalizeAndMergeModels(loadModels());
    }

    protected abstract List<Map<String,Double>> loadModels();

    public Map<String,Double> getMap() {
        return model;
    }

    // Returns value between 1 and 5
    @Override
    public Double attributesFor(Collection<String> portfolio, int n) {
        return portfolio.stream()
                .collect(Collectors.averagingDouble(token->evaluate(token)));
    }

    public double evaluate(String token) {
        if(model.containsKey(token)) {
            return model.get(token);
        } else {
            return defaultVal;
        }
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
