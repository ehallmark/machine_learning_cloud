package value_estimation;

import java.util.Collections;
import java.util.Map;

/**
 * Created by Evan on 1/27/2017.
 */
public class ValueMapNormalizer {
    public static void normalizeToRange(Map<String,Double> model, double start, double end) {
        double min = Collections.min(model.values());
        double max = Collections.max(model.values());
        model.keySet().forEach(key->{
            model.put(key,min+((model.get(key)-min)/(max-min))*(end-start));
        });
    }
}
