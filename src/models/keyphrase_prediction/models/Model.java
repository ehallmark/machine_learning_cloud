package models.keyphrase_prediction.models;

/**
 * Created by Evan on 9/15/2017.
 */
public abstract class Model {
    public abstract String getModelName();
    public double getDefaultUpperBound() { return 1d; }
    public double getDefaultLowerBound() { return 0.5d; }
    public double getDefaultMinValue() { return Double.MIN_VALUE; }
    public int getMinDocFrequency() { return 3; };
    public double getMaxDocFrequencyRatio() { return 0.15; };
    public int getSampling() { return 400000; }
}
