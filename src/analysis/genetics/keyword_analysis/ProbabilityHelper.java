package analysis.genetics.keyword_analysis;

import org.apache.commons.math3.distribution.GeometricDistribution;

import java.util.Random;

/**
 * Created by ehallmark on 2/24/17.
 */
public class ProbabilityHelper {
    private static Random random = new Random(76);
    public static int getLowNumberWithMaxUpTo(int max) {
        GeometricDistribution dist = new GeometricDistribution(4.0/max);
        return Math.min(dist.inverseCumulativeProbability(random.nextDouble())-1,max-1);
    }

    public static int getHighNumberWithMaxUpTo(int max){
        return max-1-getLowNumberWithMaxUpTo(max);
    }
}
