package models.value_models;


import lombok.Getter;
import org.deeplearning4j.berkeley.Pair;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.portfolios.items.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class ValueModelCombination extends ValueAttr {
    public static final Double DEFAULT_START = 0.0;
    public static final Double DEFAULT_END = 100.0;
    private static final boolean debug = true;
    private static final double reducePercent = 0.15;

    protected ValueAttr aiValue;
    protected ValueAttr techValue;
    @Getter
    protected String name;
    public ValueModelCombination(String name, ValueAttr aiValue, ValueAttr techValue) {
        this.techValue=techValue;
        this.aiValue=aiValue;
        this.name=name;
    }

    @Override
    public double evaluate(Item item) {
        double score = (aiValue.evaluate(item) * DEFAULT_END) * ((1d-reducePercent) + (reducePercent * techValue.evaluate(item)));;
        if(debug) {
            System.out.println("Contribution AI: " + aiValue.evaluate(item));
            System.out.println("Contribution Tech: " + techValue.evaluate(item));
            System.out.println("Score: " + score);
        }
        return score;
    }
}
