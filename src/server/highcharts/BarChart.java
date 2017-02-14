package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.SeriesType;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class BarChart extends AbstractChart {
    public BarChart(String title, List<Series<?>> data) {
        super(title, data, SeriesType.BAR);
    }
}
