package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.Options;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {
    private static final String[] HEX_COLORS = new String[]{
            "#7cb5ec", "#434348", "#90ed7d", "#f7a35c", "#8085e9", "#f15c80", "#e4d354", "#2b908f", "#f45b5b", "#91e8e1"
    };

    public static String getColor(int i) {
        return HEX_COLORS[i*HEX_COLORS.length];
    }

    protected Options options;

    public boolean isStockChart() { return false; }

    public Object getOptions() { return options; }

    public abstract String getType();

    @Override
    public String toString() {
        return new JsonRenderer().toJson(getOptions());
    }
}
