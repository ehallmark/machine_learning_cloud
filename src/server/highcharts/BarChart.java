package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.color.SimpleColor;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class BarChart extends AbstractChart {
    public BarChart(String title, List<Series<?>> data, double min, double max) {
        super(title, data, SeriesType.BAR);
        setupColumnAndBarAxes();
        options.getSingleXAxis().setMin(min).setMax(max);
    }
    public BarChart(String title, List<Series<?>> data) {
        super(title, data, SeriesType.BAR);
        setupColumnAndBarAxes();
    }
}
