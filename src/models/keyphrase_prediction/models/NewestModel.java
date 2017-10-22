package models.keyphrase_prediction.models;

import lombok.Getter;

/**
 * Created by Evan on 9/15/2017.
 */
public class NewestModel implements Model {
    @Getter
    final long Kw = 8000;
    @Getter
    final int k1 = 15;
    @Getter
    final int k2 = 5;
    @Getter
    final int k3 = 1;

    @Getter
    final int windowSize = 4;
    @Getter
    final int maxCpcLength = 8;

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
    double stage4Upper = 1d;
    @Getter
    double stage4Lower = 0.2;
    @Getter
    double stage4Min = 0.05;

    @Getter
    double stage3Upper = 1d;
    @Getter
    double stage3Lower = 0.2;
    @Getter
    double stage3Min = 0d;

    @Getter
    double stage1Upper = 0.95;
    @Getter
    double stage1Lower = 0.05;

    @Getter
    double stage2Upper = 0.95;
    @Getter
    double stage2Lower = 0.05;
    @Getter
    double stage2Min = Double.MIN_VALUE;

}
