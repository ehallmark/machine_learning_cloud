package models.keyphrase_prediction.models;

/**
 * Created by ehallmark on 12/21/17.
 */
public class DefaultModel2 extends Model {
    @Override
    public String getModelName() {
        return "vae_model";
    }

    public double getDefaultUpperBound() { return 1d; }
    public double getDefaultLowerBound() { return 0.6d; }
    public double getDefaultMinValue() { return Double.MIN_VALUE; }
    public int getMinDocFrequency() { return 5; };
    public double getMaxDocFrequencyRatio() { return 0.15; };
    public int getSampling() { return 400000; }
}
