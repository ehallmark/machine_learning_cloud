package models.keyphrase_prediction.models;

/**
 * Created by ehallmark on 12/21/17.
 */
public class DefaultModel3 extends Model {
    @Override
    public String getModelName() {
        return "wordcpc2vec_model";
    }

    public double getDefaultUpperBound() { return 1d; }
    public double getDefaultLowerBound() { return 0.5d; }
    public double getDefaultMinValue() { return Double.MIN_VALUE; }
    public int getMinDocFrequency() { return 20; };
    public double getMaxDocFrequencyRatio() { return 0.10; };
    public int getSampling() { return 600000; }
}
