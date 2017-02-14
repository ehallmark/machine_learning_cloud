package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.SeriesType;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class ColumnChart extends AbstractChart {
    public ColumnChart(String title, List<Series<?>> data, double min, double max) {
        super(title, data, SeriesType.COLUMN);
        setupColumnAndBarAxes();
        options.getSingleXAxis().setMin(min).setMax(max);
    }
    public ColumnChart(String title, List<Series<?>> data) {
        super(title, data, SeriesType.COLUMN);
        setupColumnAndBarAxes();
    }
}
