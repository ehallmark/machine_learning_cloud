package models.keyphrase_prediction.models;

import lombok.Getter;

/**
 * Created by Evan on 9/15/2017.
 */
public class TimeDensityModel implements Model {
    @Getter
    final long Kw = 10000;
    @Getter
    final int k1 = 10;
    @Getter
    final int k2 = 10;
    @Getter
    final int k3 = 1;

    @Getter
    final int maxCpcLength = 8;

    @Override
    public String getModelName() {
        return "timedensity";
    }

    @Getter
    double stage4Upper = 0.95;
    @Getter
    double stage4Lower = 0.4;
    @Getter
    double stage4Min = Double.MIN_VALUE;

    @Getter
    double stage3Upper = 0.95;
    @Getter
    double stage3Lower = 0.4;
    @Getter
    double stage3Min = Double.MIN_VALUE;

    @Getter
    double stage2Upper = 0.95;
    @Getter
    double stage2Lower = 0.4;

    @Getter
    double stage1Upper = 0.9;
    @Getter
    double stage1Lower = 0.3;
}
