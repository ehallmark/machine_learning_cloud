package ui_models.attributes.value;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.distribution.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class ValueMapNormalizer {
    public static final Double DEFAULT_START = 1.0;
    public static final Double DEFAULT_END = 5.0;
    private DistributionType type;

    public enum DistributionType {
        Normal, Exponentional, None, Uniform
    }

    public ValueMapNormalizer(DistributionType type) {
        this.type=type;
    }

    public Map<String,Double> normalizeAndMergeModels(Collection<Map<String,Double>> maps) {
        Map<String,Double> merged = new HashMap<>();
        maps.forEach(map->{
            if(!type.equals(DistributionType.None)) {
                normalizeToRange(map);
            }
            map.forEach((k,v)->{
                if(merged.containsKey(k)) {
                    merged.put(k,merged.get(k)+v);
                } else {
                    merged.put(k,v);
                }
            });
        });
        if(maps.size()>1) {
            merged.keySet().forEach(key -> {
                merged.put(key, merged.get(key) / maps.size());
            });
        }
        return merged;
    }

    private void normalizeToRange(Map<String,Double> model) {
        List<String> keys = new ArrayList<>(model.keySet());
        List<Double> values = new ArrayList<>(keys.size());
        for(String key : keys) {
            values.add(model.get(key));
        }
        INDArray array = Nd4j.create(ArrayUtils.toPrimitive(values.toArray(new Double[values.size()])));
        double stdDev = Math.sqrt(array.varNumber().doubleValue());
        double mean = array.meanNumber().doubleValue();
        double max = array.maxNumber().doubleValue();
        double min = array.minNumber().doubleValue();

        AbstractRealDistribution distribution;
        switch(type) {
            case Normal: {
                distribution=new NormalDistribution(mean,stdDev);
            } break;
            case Exponentional: {
                distribution=new ExponentialDistribution(mean);
                break;
            } case Uniform: {
                if(min>=max) throw new RuntimeException("Unable to create uniform distribution");
                distribution= new UniformRealDistribution(min,max);
                break;
            }
            default: {
                throw new RuntimeException("No distribution specified");
            }

        }
        for(int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            double value = distribution.cumulativeProbability(array.getDouble(i))*(DEFAULT_END-DEFAULT_START)+DEFAULT_START;
            model.put(key,value);
        }
    }
}
