package user_interface.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.SimpleSeries;

import java.util.Arrays;

/**
 * Created by ehallmark on 2/13/17.
 */
public class Test {

    public static Options getTestOptions() {
        Options options = new Options();

        options
                .setChartOptions(new ChartOptions()
                        .setType(SeriesType.LINE));

        options
                .setTitle(new Title("My very own chart."));

        options
                .setxAxis(new Axis()
                        .setCategories(Arrays
                                .asList(new String[] { "Jan", "Feb", "Mar", "Apr", "May",
                                        "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" })));

        options
                .setyAxis(new Axis()
                        .setTitle(new Title("Temperature (C)")));

        options
                .setLegend(new Legend()
                        .setLayout(LegendLayout.VERTICAL)
                        .setAlign(HorizontalAlignment.RIGHT)
                        .setVerticalAlign(VerticalAlignment.TOP)
                        .setX(-10)
                        .setY(100)
                        .setBorderWidth(0));

        options
                .addSeries(new SimpleSeries()
                        .setName("Tokyo")
                        .setData(
                                Arrays
                                        .asList(new Number[] { 7.0, 6.9, 9.5, 14.5, 18.2, 21.5,
                                                25.2, 26.5, 23.3, 18.3, 13.9, 9.6 })));

        options
                .addSeries(new SimpleSeries()
                        .setName("New York")
                        .setData(
                                Arrays
                                        .asList(new Number[] { -0.2, 0.8, 5.7, 11.3, 17.0, 22.0,
                                                24.8, 24.1, 20.1, 14.1, 8.6, 2.5 })));
        return options;
    }
    public static void main(String[] args) {
        // user_interface.highcharts test
        Options options = getTestOptions();
        System.out.println(new JsonRenderer().toJson(options).toString());
    }
}
