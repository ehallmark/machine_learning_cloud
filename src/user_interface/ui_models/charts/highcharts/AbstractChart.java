package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.Options;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {

    private static final List<Function<Double,String>> RGB_COLORS = Arrays.asList(
            alpha -> "rgb(124,128,236,"+alpha+")",
            alpha -> "rgb(67,67,72,"+alpha+")",
            alpha -> "rgb(144,237,125,"+alpha+")",
            alpha -> "rgb(247,163,92,"+alpha+")",
            alpha -> "rgb(128,133,233,"+alpha+")",
            alpha -> "rgb(241,92,128,"+alpha+")",
            alpha -> "rgb(228,211,84,"+alpha+")",
            alpha -> "rgb(43,144,143,"+alpha+")",
            alpha -> "rgb(244,91,91,"+alpha+")",
            alpha -> "rgb(145,232,225,"+alpha+")"
    );

    public static String getColor(int i, double alpha) {
        return RGB_COLORS.get(i%RGB_COLORS.size()).apply(alpha);
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
