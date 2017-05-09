package highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class BarChart extends AbstractChart {
    public BarChart(String title, List<Series<?>> data, double min, double max, String valueSuffix, int decimals) {
        super(title, data, SeriesType.BAR,decimals,valueSuffix);
        setupColumnAndBarAxes(valueSuffix,decimals);
        options.getSingleYAxis().setMin(min).setMax(max);
    }
    public BarChart(String title, List<Series<?>> data, double min, double max, String valueSuffix) {
        this(title,data,min,max,valueSuffix,1);
    }
    public BarChart(String title, List<Series<?>> data, double min, double max) {
        this(title,data,min,max,"");
    }
}
