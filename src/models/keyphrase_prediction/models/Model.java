package models.keyphrase_prediction.models;

/**
 * Created by Evan on 9/15/2017.
 */
public interface Model {
    long getKw();
    double getStage1Lower();
    double getStage1Upper();
    int getMaxCpcLength();
    String getModelName();
    double getStage3Lower();
    double getStage3Upper();
    double getStage4Lower();
    double getStage4Upper();
    double getStage4Min();
    double getStage3Min();
    int getMinDocFrequency();
    default int getSampling() { return 400000; }
}
