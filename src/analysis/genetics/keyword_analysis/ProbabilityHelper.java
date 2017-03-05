package analysis.genetics.keyword_analysis;

import org.apache.commons.math3.distribution.GeometricDistribution;

import java.util.Random;

/**
 * Created by ehallmark on 2/24/17.
 */
public class ProbabilityHelper {
    private static Random random = new Random(76);
    public static int getLowNumberWithMaxUpTo(int max) {
        GeometricDistribution dist = new GeometricDistribution(1.0 / Math.cbrt(max));
        int num = Math.max(0, Math.min(dist.inverseCumulativeProbability(random.nextDouble()) - 1, max - 1));
        return num;
    }

    public static int getHighNumberWithMaxUpTo(int max){
        return max-1-getLowNumberWithMaxUpTo(max);
    }
}
