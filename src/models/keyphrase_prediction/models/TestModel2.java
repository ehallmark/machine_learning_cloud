package models.keyphrase_prediction.models;

import lombok.Getter;

/**
 * Created by Evan on 9/15/2017.
 */
public abstract class TestModel2 implements Model {
    @Getter
    final long Kw = 10000;
    @Getter
    final int k1 = 10;
    @Getter
    final int k2 = 5;
    @Getter
    final int k3 = 1;

    @Getter
    final int minTokenFrequency = 10;
    @Getter
    final int maxTokenFrequency = 100000;

    @Getter
    final int windowSize = 4;
    @Getter
    final int maxCpcLength = 9;

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
        return "test1";
    }

    @Getter
    double stage4Upper = 0.95;
    @Getter
    double stage4Lower = 0.2;

    @Getter
    double stage3Upper = 0.95;
    @Getter
    double stage3Lower = 0.2;

    @Getter
    double stage2Upper = 0.9;
    @Getter
    double stage2Lower = 0.0;
}
