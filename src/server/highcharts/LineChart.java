package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.Axis;
import com.googlecode.wickedcharts.highcharts.options.AxisType;
import com.googlecode.wickedcharts.highcharts.options.SeriesType;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class LineChart extends AbstractChart {
    public LineChart(String title, List<Series<?>> data, AxisType axisType, int numDecimals, String valueSuffix) {
        super(title, data, SeriesType.LINE, numDecimals, valueSuffix);
        // date axis
        Axis dateTimeAxis = new Axis(axisType);
        options.setxAxis(dateTimeAxis);
    }

    public LineChart(String title, List<Series<?>> data, AxisType axisType, String valueSuffix) {
        this(title,data,axisType,0,valueSuffix);
    }
}
