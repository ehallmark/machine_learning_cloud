package user_interface.highcharts;

import com.googlecode.wickedcharts.highcharts.options.SeriesType;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class ColumnChart extends AbstractChart {
    public ColumnChart(String title, List<Series<?>> data, Double min, Double max, String valueSuffix, int decimals, String xLabel, String yLabel) {
        super(title, data, SeriesType.COLUMN,decimals,valueSuffix,xLabel,yLabel);
        setupColumnAndBarAxes(valueSuffix,decimals);
        if(min!=null) options.getSingleYAxis().setMin(min);
        if(max!=null&&max>min) options.getSingleYAxis().setMax(max);
    }
}

