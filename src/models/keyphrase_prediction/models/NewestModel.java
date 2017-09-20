package models.keyphrase_prediction.models;

import lombok.Getter;

/**
 * Created by Evan on 9/15/2017.
 */
public class NewestModel implements Model {
    @Getter
    final long Kw = 5000;
    @Getter
    final int k1 = 10;
    @Getter
    final int k2 = 3;
    @Getter
    final int k3 = 1;

    @Getter
    final int minTokenFrequency = 30;
    @Getter
    final int maxTokenFrequency = 200000;

    @Getter
    final int windowSize = 4;
    @Getter
    final int maxCpcLength = 7;

    @Getter
    boolean runStage1 = true;
    @Getter
    boolean runStage2 = true;
    @Getter
    boolean runStage3 = true;
    @Getter
    boolean runStage4 = true;
    @Getter
    boolean runStage5 = true;

    @Override
    public String getModelName() {
        return "latest";
    }

    @Getter
    double stage4Upper = 1;
    @Getter
    double stage4Lower = 0.2;
    @Getter
    double stage4Min = 0.05;

    @Getter
    double stage3Upper = 0.95;
    @Getter
    double stage3Lower = 0.2;
    @Getter
    double stage3Min = 0d;

    @Getter
    double stage2Upper = 0.80;
    @Getter
    double stage2Lower = 0.0;
    @Getter
    double stage2Min = minTokenFrequency;
}
