package models.keyphrase_prediction.models;

import lombok.Getter;

/**
 * Created by Evan on 9/15/2017.
 */
public class TimeDensityModel implements Model {
    @Getter
    final long Kw = 500000;

    @Getter
    final int maxCpcLength = 9;

    @Override
    public String getModelName() {
        return "timedensity";
    }

    @Override
    public  int getSampling() { return 500000; }

    @Getter
    double stage4Upper = 1d;
    @Getter
    double stage4Lower = 0.25;
    @Getter
    double stage4Min = 0d;

    @Getter
    double stage3Upper = 1d;
    @Getter
    double stage3Lower = 0.25;
    @Getter
    double stage3Min = 0d;

    @Getter
    double stage2Upper = 1d;
    @Getter
    double stage2Lower = 0.25;

    @Getter
    double stage1Upper = 0.8;
    @Getter
    double stage1Lower = 0.0;
    @Getter
    int minDocFrequency = 3;
}
