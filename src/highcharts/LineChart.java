package highcharts;

import com.googlecode.wickedcharts.highcharts.options.Axis;
import com.googlecode.wickedcharts.highcharts.options.AxisType;
import com.googlecode.wickedcharts.highcharts.options.SeriesType;
import com.googlecode.wickedcharts.highcharts.options.Title;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class LineChart extends AbstractChart {
    public LineChart(String title, List<Series<?>> data, AxisType axisType, int numDecimals, String valueSuffix, String yAxisLabel) {
        super(title, data, SeriesType.LINE, numDecimals, valueSuffix);
        // date axis
        Axis dateTimeAxis = new Axis(axisType);
        options.setxAxis(dateTimeAxis);
        options.getSingleYAxis().setTitle(new Title(yAxisLabel));

    }

    public LineChart(String title, List<Series<?>> data, AxisType axisType, String valueSuffix, String yAxisLabel) {
        this(title,data,axisType,0,valueSuffix, yAxisLabel);
    }
}
