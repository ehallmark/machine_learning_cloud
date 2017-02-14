package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.SeriesType;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class LineChart extends AbstractChart {
    public LineChart(String title, List<Series<?>> data) {
        super(title, data, SeriesType.LINE);
    }
}
