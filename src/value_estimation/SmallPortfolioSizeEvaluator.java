package value_estimation;

/**
 * Created by Evan on 2/26/2017.
 */
public class SmallPortfolioSizeEvaluator extends PortfolioSizeEvaluator {

    public SmallPortfolioSizeEvaluator() {
        super("Small Portfolio Size Value");
    }

    // Returns value between 1 and 5
    @Override
    public double evaluate(String token) {
        // flip the value
        if(model.containsKey(token)) {
            double val = model.get(token);
            double distFromStart = val - ValueMapNormalizer.DEFAULT_START;
            val = ValueMapNormalizer.DEFAULT_END-distFromStart;
            return val;
        } else {
            return ValueMapNormalizer.DEFAULT_START;
        }
    }
}
