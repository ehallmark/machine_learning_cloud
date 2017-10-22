package models.keyphrase_prediction.models;

import lombok.Getter;

/**
 * Created by Evan on 9/15/2017.
 */
public class TimeDensityModel implements Model {
    @Getter
    final long Kw = 50000;
    @Getter
    final int k1 = 20;
    @Getter
    final int k2 = 10;
    @Getter
    final int k3 = 2;

    @Getter
    final int minTokenFrequency = 30;
    @Getter
    final int maxTokenFrequency = Math.round(getSampling() * 0.3f);

    @Getter
    final int maxCpcLength = 8;

    @Override
    public String getModelName() {
        return "timedensity";
    }

    @Getter
    double stage4Upper = 1d;
    @Getter
    double stage4Lower = 0.2;
    @Getter
    double stage4Min = 0d;

    @Getter
    double stage3Upper = 1d;
    @Getter
    double stage3Lower = 0.2;
    @Getter
    double stage3Min = 0d;

    @Getter
    double stage2Upper = 0.95;
    @Getter
    double stage2Lower = 0.05;
}
