package models.value_models.regression;

import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.portfolios.items.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Evan on 9/3/2017.
 */
public abstract class RegressionValueModel extends ValueAttr {
    protected List<AbstractAttribute> attributes;
    protected List<Double> weights;
    protected List<Pair<AbstractAttribute,Double>> attributesAndWeights;
    protected double intercept;
    public RegressionValueModel(double intercept, List<AbstractAttribute> attributes, List<Double> weights) {
        this.attributes=attributes;
        this.weights = weights;
        this.intercept=intercept;
        attributesAndWeights = new ArrayList<>();
        if(weights!=null&&attributes!=null) {
            for (int i = 0; i < attributes.size(); i++) {
                attributesAndWeights.add(new Pair<>(attributes.get(i), weights.get(i)));
            }
        }
    }

    @Override
    public double evaluate(Item item) {
        return 1d/(1d+Math.exp(-(intercept+attributesAndWeights.stream().mapToDouble(pair->(valueFromAttribute(item,pair.getFirst(),defaultVal)).doubleValue()*pair.getSecond()).sum())));
    }

    private static Number valueFromAttribute(Item item, AbstractAttribute attribute, Number defaultVal) {
        Number val = (Number) item.getDataMap().get(attribute.getFullName());
        if(val == null && attribute instanceof ComputableAttribute) {
            val = (Number) ((ComputableAttribute) attribute).getApplicationDataMap().getOrDefault(item.getName(),((ComputableAttribute) attribute).getPatentDataMap().get(item.getName()));
        }
        if(val == null) val = defaultVal;
        return val;
    }

    public static void main(String[] args) {
        //test
        RegressionValueModel model = new RegressionValueModel(0.5, Arrays.asList(new PageRankEvaluator()),Arrays.asList(200000d)) {
            @Override
            public String getName() {
                return "test";
            }
        };

        Database.getCopyOfAllPatents().stream().limit(100).forEach(patent->{
           Item item = new Item(patent);
           System.out.println("Prediction for model: "+model.evaluate(item));
        });
    }
}
