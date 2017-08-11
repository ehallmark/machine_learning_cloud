package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import models.value_models.*;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ValueAttr extends ComputableAttribute<Number> {
    // Instance class
    protected ValueMapNormalizer.DistributionType distributionType;
    private static final double DEFAULT_VAL = (ValueMapNormalizer.DEFAULT_START+ValueMapNormalizer.DEFAULT_END)/2d;
    protected double defaultVal;

    public ValueAttr(ValueMapNormalizer.DistributionType distributionType, double defaultVal) {
        super(Arrays.asList(AbstractFilter.FilterType.GreaterThan));
        this.defaultVal=defaultVal;
        this.distributionType=distributionType;
    }

    public ValueAttr(ValueMapNormalizer.DistributionType distributionType) {
        this(distributionType, DEFAULT_VAL);
    }

    // Returns value between 1 and 5
    @Override
    public Double attributesFor(Collection<String> portfolio, int n) {
        return portfolio.stream()
                .collect(Collectors.averagingDouble(token->evaluate(token)));
    }

    @Override
    public synchronized Map<String,Number> getPatentDataMap() {
        if(patentDataMap==null) {
            patentDataMap = (Map<String,Number>) Database.tryLoadObject(dataFileFrom(Constants.PATENT_DATA_FOLDER,getName(),getType()));
            if(distributionType!=null) patentDataMap = new ValueMapNormalizer(distributionType).normalizeAndMergeModels(Arrays.asList(patentDataMap));
        }
        return patentDataMap;
    }

    @Override
    public synchronized Map<String,Number> getApplicationDataMap() {
        if(applicationDataMap==null) {
            applicationDataMap = (Map<String,Number>) Database.tryLoadObject(dataFileFrom(Constants.APPLICATION_DATA_FOLDER,getName(),getType()));
            if(distributionType!=null) applicationDataMap = new ValueMapNormalizer(distributionType).normalizeAndMergeModels(Arrays.asList(applicationDataMap));
        }
        return applicationDataMap;
    }

    public double evaluate(String token) {
        Map<String,Number> model = Database.isApplication(token) ? getApplicationDataMap() : getPatentDataMap();
        if(model.containsKey(token)) {
            return model.get(token).doubleValue();
        } else {
            return defaultVal;
        }
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public String getType() {
        return "double";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }
}
