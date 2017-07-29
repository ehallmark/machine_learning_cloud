package models.model_testing;

import org.apache.commons.lang3.ArrayUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import models.value_models.PageRankEvaluator;
import user_interface.ui_models.attributes.ValueAttr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 5/17/2017.
 */
public class EvaluationAttrStatistics {
    public static void main(String[] args) {
        Database.initializeDatabase();
        ValueAttr attr = new PageRankEvaluator(true);
        Map<String,Double> model = attr.getModel();
        List<String> keys = new ArrayList<>(model.keySet());
        List<Double> values = new ArrayList<>(keys.size());
        for(String key : keys) {
            values.add(model.get(key));
        }
        INDArray array = Nd4j.create(ArrayUtils.toPrimitive(values.toArray(new Double[values.size()])));
        double stdDev = Math.sqrt(array.varNumber().doubleValue());
        double mean = array.meanNumber().doubleValue();
        double max = array.maxNumber().doubleValue();
        double min = 0d;

        System.out.println("N: "+keys.size());
        System.out.println("Mean: "+mean);
        System.out.println("Stddev: "+stdDev);
        System.out.println("Max: "+max);
        System.out.println("Min: "+min);
    }
}
