package analysis.genetics.keyword_analysis;

import java.util.Random;

/**
 * Created by ehallmark on 2/24/17.
 */
public class ProbabilityHelper {
    private static Random random = new Random(76);
    public static int getLowNumberWithMaxUpTo(int max) {
        return Math.max(0,Math.round((float)Math.pow(random.nextInt(max),0.5)));
    }

    public static int getHighNumberWithMaxUpTo(int max){
        return Math.min(max-1,max-1-Math.round((float)Math.pow(random.nextInt(max),0.5)));
    }
}
