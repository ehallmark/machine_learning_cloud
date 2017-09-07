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

    protected List<Pair<ValueAttr,Double>> pairs;
    @Getter
    protected String name;
    public ValueModelCombination(String name, List<ValueAttr> valueAttrs, List<Double> weights) {
        if(valueAttrs.size()!=weights.size()) throw new RuntimeException("Weights and models must have same cardinality");
        // normalize weights to sum to 1
        this.name=name;
        this.pairs = new ArrayList<>();
        for(int i = 0; i < weights.size(); i++ ){
            pairs.add(new Pair<>(valueAttrs.get(i),weights.get(i)));
        }
    }

    @Override
    public double evaluate(Item item) {
        return pairs.stream().collect(Collectors.summingDouble(pair->pair.getFirst().evaluate(item)*pair.getSecond()));
    }
}
