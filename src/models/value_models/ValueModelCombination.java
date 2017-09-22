package models.value_models;


import lombok.Getter;
import org.deeplearning4j.berkeley.Pair;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.portfolios.items.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class ValueModelCombination extends ValueAttr {
    public static final Double DEFAULT_START = 0.0;
    public static final Double DEFAULT_END = 100.0;
    private static final boolean debug = false;
    private static final double reducePercent = 0.15;

    protected ValueAttr aiValue;
    protected ValueAttr techValue;
    @Getter
    protected String name;
    protected boolean train;
    public ValueModelCombination(String name, ValueAttr aiValue, ValueAttr techValue, boolean train) {
        this.techValue=techValue;
        this.aiValue=aiValue;
        this.name=name;
        this.train=train;
    }

    @Override
    public double evaluate(Item item) {
        double score;
        if(train) {
            score = (aiValue.evaluate(item) * DEFAULT_END) * ((1d - reducePercent) + (reducePercent * techValue.evaluate(item)));
        } else {
            if(applicationDataMap==null) {
                synchronized (this) {
                    if(applicationDataMap==null) {
                        applicationDataMap = getApplicationDataMap();
                    }
                }
            }
            if(patentDataMap==null) {
                synchronized (this) {
                    if(patentDataMap==null) {
                        patentDataMap = getApplicationDataMap();
                    }
                }
            }
            score = patentDataMap.getOrDefault(item.getName(),applicationDataMap.getOrDefault(item.getName(),defaultVal)).doubleValue();
        }
        if(debug) {
           // System.out.println("Contribution AI: " + aiValue.evaluate(item));
           // System.out.println("Contribution Tech: " + techValue.evaluate(item));
            System.out.println("Score: " + score);
        }
        return score;
    }

    @Override
    public String getName() {
        return name;
    }
}
