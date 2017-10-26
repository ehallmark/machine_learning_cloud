package models.keyphrase_prediction.models;

/**
 * Created by Evan on 9/15/2017.
 */
public abstract class Model {
    public abstract String getModelName();
    public double getDefaultUpperBound() { return 1d; }
    public double getDefaultLowerBound() { return 0.15; }
    public double getDefaultMinValue() { return 0.0; }
    public int getMinDocFrequency() { return 5; };
    public int getSampling() { return 200000; }
}
