package server.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {
    protected Options options;

    public Options getOptions() {
        return options;
    }

    protected AbstractChart(String title, List<Series<?>> data, SeriesType type) {
        options=new Options()
                .setChartOptions(new ChartOptions().setType(type))
                .setTitle(new Title(title))
                .setSeries(data);
    }

    @Override
    public String toString() {
        return new JsonRenderer().toJson(options).toString();
    }
}
