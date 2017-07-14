package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {
    protected Options options;

    public Options getOptions() { return options; }

    public abstract String getType();

    @Override
    public String toString() {
        return new JsonRenderer().toJson(getOptions());
    }
}
