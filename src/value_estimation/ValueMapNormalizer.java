package value_estimation;

import org.apache.commons.lang3.ArrayUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class ValueMapNormalizer {
    public static void normalizeToRange(Map<String,Double> model, double start, double end) {
        List<Double> values = model.entrySet().stream().map(e->e.getValue()).collect(Collectors.toList());
        INDArray array = Nd4j.create(ArrayUtils.toPrimitive(values.toArray(new Double[values.size()])));
        double stdDev = Math.sqrt(array.varNumber().doubleValue());
        double mean = array.meanNumber().doubleValue();
        double min = mean-2*stdDev;
        double max = mean+2.5*stdDev;
        model.keySet().forEach(key->{
            model.put(key,Math.max(Math.min(end,start+((model.get(key)-min)/(max-min))*(end-start)),start));
        });
    }
}
