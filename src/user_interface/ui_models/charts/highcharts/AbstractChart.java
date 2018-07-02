package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.Options;

import java.util.Arrays;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {

    public static final List<int[]> RGB_COLORS = Arrays.asList(
            new int[]{124,181,236},
            new int[]{67,67,72},
            new int[]{144,237,125},
            new int[]{247,163,92},
            new int[]{128,133,233},
            new int[]{241,92,128},
            new int[]{228,211,84},
            new int[]{43,144,143},
            new int[]{244,91,91},
            new int[]{145,232,225}
    );

    public static String convertToRGB(int r, int g, int b, int brightenPercent) {
        r += (brightenPercent * (255-r)) / 100;
        g += (brightenPercent * (255-g)) / 100;
        b += (brightenPercent * (255-b)) / 100;
        return "rgb("+r+","+g+","+b+", 1.0)";
    }


    public static int[] brighten(int r, int g, int b, int brightenPercent) {
        r += (brightenPercent * (255-r)) / 100;
        g += (brightenPercent * (255-g)) / 100;
        b += (brightenPercent * (255-b)) / 100;
        return new int[]{r,g,b};
    }

    public static String getColor(int i, int brightenPercent) {
        int[] rgb = RGB_COLORS.get(i%RGB_COLORS.size());
        return convertToRGB(rgb[0], rgb[1], rgb[2], brightenPercent);
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
