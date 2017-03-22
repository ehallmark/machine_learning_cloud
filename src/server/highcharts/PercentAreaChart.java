package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 3/20/17.
 */
public class PercentAreaChart extends AbstractChart {
    public PercentAreaChart(String title, List<Series<?>> data) {
        super(title, data, SeriesType.AREA, 1, "%");
        for(Series<?> series : options.getSeries()) {
            series.setDataLabels(new DataLabels(false));
        }
        options.setTooltip(new Tooltip().setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> {point.name}:<b> {point.percentage:.1f}</b><br/>"));
        options.getPlotOptions().setArea(new PlotOptions().setStacking(Stacking.PERCENT).setLineWidth(1).setLineColor(Color.white)
            .setMarker(new Marker().setLineColor(Color.white).setLineWidth(1)));
        options.getSingleYAxis().setTitle(new Title("Percent")).setMax(100).setMin(0);
        options.getSingleXAxis().setType(AxisType.DATETIME);
    }
}
