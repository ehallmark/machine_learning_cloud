package graphical_modeling.model.functions.normalization;

import java.util.Arrays;

/**
 * Created by ehallmark on 4/26/17.
 */
public class DivideByPartition implements NormalizationFunction {
    public void normalize(double[] weights) {
        double sum = Arrays.stream(weights).sum();
        for(int i = 0; i < weights.length; i++) {
            weights[i]/=sum;
        }
    }
}
