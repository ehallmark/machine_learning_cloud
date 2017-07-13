package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class PieChart extends AbstractChart {
    public PieChart(String title, List<Series<?>> data, String searchType) {
        super(title, data, SeriesType.PIE,1,"%",null,searchType);
        for(Series<?> series : options.getSeries()) {
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat("<b>{point.name}</b>: {point.percentage:.1f}%")
                    .setY(-5)
            );
        }
        options.setTooltip(new Tooltip().setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> {point.name}:<b> {point.percentage:.1f}%</b><br/>Count: <b> {point.y} "+yLabel+"</b><br/>"));
    }
}
