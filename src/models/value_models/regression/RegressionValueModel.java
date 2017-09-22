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
import java.util.Map;

/**
 * Created by Evan on 9/3/2017.
 */
public abstract class RegressionValueModel extends ValueAttr {
    private static final boolean debug = false;
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
        Number val=null;
        Object obj = item.getDataMap().get(attribute.getFullName());
        if(obj == null && attribute instanceof ComputableAttribute) {
            // check for filing
            obj = ((ComputableAttribute) attribute).getApplicationDataMap().getOrDefault(item.getName(),((ComputableAttribute) attribute).getPatentDataMap().get(item.getName()));
        }
        if(obj!=null) {
            if(obj.toString().equals("false")) obj = "0";
            else if(obj.toString().equals("true")) obj = "1";
            val = Double.valueOf(obj.toString());
        }
        if(val == null) val = defaultVal;
        if(debug) System.out.println("Val for "+attribute.getName()+": "+val);
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
