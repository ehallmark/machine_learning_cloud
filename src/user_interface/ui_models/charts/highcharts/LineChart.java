package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class LineChart extends AbstractChart {
    @Override
    public String getType() {
        return "timeline";
    }

    public LineChart(String title, List<Series<?>> data, String xAxisSuffix, String yAxisSuffix, String xLabel, String yLabel, int yDecimals) {
        String yFormatStr = "{point.y:."+yDecimals+"f}"+yAxisSuffix;
        String xFormatStr = "{point.key}"+xAxisSuffix;
        options=new Options()
                .setChartOptions(new ChartOptions().setHeight(450).setType(SeriesType.LINE))
                .setTitle(new Title(title))
                .setExporting(new ExportingOptions().setEnabled(true))
                .setTooltip(new Tooltip().setEnabled(true).setHeaderFormat(xFormatStr+"<br/>").setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> <b> Count: "+yFormatStr+" "+yLabel+"</b><br/>"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setSeries(data);
        options.setxAxis(new Axis().setType(AxisType.LINEAR).setTitle(new Title(xLabel)));
        options.setyAxis(new Axis().setType(AxisType.LINEAR).setTitle(new Title(ColumnChart.capitalize(yLabel)+" Count")));
        for(Series<?> series : options.getSeries()) {
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat(yFormatStr+" "+yLabel)
                    .setY(-5)
            );
        }
    }
    
}

