package data_pipeline.optimize.parameters.distributions;

import com.google.common.util.concurrent.AtomicDouble;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by ehallmark on 11/10/17.
 */
public class DiscreteDistribution<T> implements ParameterDistribution<T> {
    private Random rand;
    private final List<T> orderedItems;
    private final List<Double> probabilities;
    public DiscreteDistribution(Map<T,? extends Number> probabilityMap) {
        this.rand = new Random(System.currentTimeMillis());
        if(probabilityMap.isEmpty()) throw new RuntimeException("Discrete distribution cannot be empty.");
        this.orderedItems=new ArrayList<>(probabilityMap.size());
        this.probabilities=new ArrayList<>(probabilityMap.size());
        AtomicDouble sum = new AtomicDouble(0d);
        probabilityMap.forEach((item,val)->{
            sum.addAndGet(val.doubleValue());
            orderedItems.add(item);
            probabilities.add(val.doubleValue());
        });
        if(sum.get()<=0d) throw new RuntimeException("Probabilities must have a positive sum.");
        for(int i = 0; i < probabilities.size(); i++) {
            probabilities.set(i,probabilities.get(i)/sum.get());
        }
    }
    public DiscreteDistribution(List<T> orderedItems) {
        this.orderedItems=orderedItems;
        this.probabilities=null;
        this.rand = new Random(System.currentTimeMillis());
    }

    @Override
    public T nextSample() {
        double r = rand.nextDouble();
        double s = 0d;
        T item = null;
        if(probabilities!=null) {
            for (int i = 0; i < probabilities.size(); i++) {
                s += probabilities.get(i);
                if (r <= s) {
                    item = orderedItems.get(i);
                    break;
                }
            }
        }
        // fall back to uniform
        if(item==null) {
            item = orderedItems.get(rand.nextInt(orderedItems.size()));
        }
        return item;
    }
}
