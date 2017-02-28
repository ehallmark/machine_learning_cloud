package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.SeriesType;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class ColumnChart extends AbstractChart {
    public ColumnChart(String title, List<Series<?>> data, Double min, Double max, String valueSuffix, String pointFormat) {
        super(title, data, SeriesType.COLUMN);
        setupColumnAndBarAxes(valueSuffix,pointFormat);
        if(min!=null) options.getSingleYAxis().setMin(min);
        if(max!=null&&max>min) options.getSingleYAxis().setMax(max);
    }
    public ColumnChart(String title, List<Series<?>> data, Double min, Double max, String valueSuffix) {
        this(title,data,min,max,valueSuffix,null);
    }
    public ColumnChart(String title, List<Series<?>> data, Double min, Double max) {
        this(title,data,min,max,"");
    }
}

