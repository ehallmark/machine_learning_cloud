package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.Options;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {
    protected Options options;

    public boolean isStockChart() { return false; }

    public Object getOptions() { return options; }

    public abstract String getType();

    @Override
    public String toString() {
        return new JsonRenderer().toJson(getOptions());
    }
}
