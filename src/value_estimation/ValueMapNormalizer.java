package value_estimation;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class ValueMapNormalizer {
    private static final NormalDistribution normalDistribution = new NormalDistribution(0d,1d);

    public static void normalizeToRange(Map<String,Double> model, double start, double end) {
        List<String> keys = new ArrayList<>(model.keySet());
        List<Double> values = new ArrayList<>(keys.size());
        for(String key : keys) {
            values.add(model.get(key));
        }
        INDArray array = Nd4j.create(ArrayUtils.toPrimitive(values.toArray(new Double[values.size()])));
        double stdDev = Math.sqrt(array.varNumber().doubleValue());
        double mean = array.meanNumber().doubleValue();
        array.subi(mean).divi(stdDev);
        // array is now standard normal

        for(int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            double value = normalDistribution.cumulativeProbability(array.getDouble(i))*(end-start)+start;
            model.put(key,value);
        }
    }
}
