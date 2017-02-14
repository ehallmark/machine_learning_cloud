package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.SeriesType;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class PieChart extends AbstractChart {
    public PieChart(String title, List<Series<?>> data) {
        super(title, data, SeriesType.PIE);
    }
}
