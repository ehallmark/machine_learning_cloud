package models.keyphrase_prediction.models;

/**
 * Created by Evan on 9/15/2017.
 */
public interface Model {
    long getKw();
    int getK1();
    int getK2();
    int getK3();
    int getMinTokenFrequency();
    int getMaxTokenFrequency();
    int getMaxCpcLength();
    String getModelName();
    double getStage3Lower();
    double getStage3Upper();
    double getStage4Lower();
    double getStage4Upper();
    double getStage4Min();
    double getStage3Min();
    default int getSampling() { return 400000; }
}
