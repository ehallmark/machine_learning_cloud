package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class ColumnChart extends AbstractChart {
    public ColumnChart(String title, List<Series<?>> data, Double min, Double max, String xAxisSuffix, String yAxisSuffix, String xLabel, String yLabel, int xDecimals, int yDecimals) {
        String yFormatStr = "{point.y:."+yDecimals+"f}"+yAxisSuffix;
        String xFormatStr = "{point.x:."+xDecimals+"f}"+xAxisSuffix;
        SeriesType type = SeriesType.COLUMN;
        options=new Options()
                .setChartOptions(new ChartOptions().setHeight(450).setType(type))
                .setTitle(new Title(title))
                .setTooltip(new Tooltip().setEnabled(true).setFormatter(new CustomFunction( "highchartsHistogramLabelFunction()")).setHeaderFormat(xFormatStr+" "+xLabel+" Range<br/>").setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> {point.name} <b> Total: "+yFormatStr+" "+yLabel+"</b><br/>"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setSeries(data)
                .setyAxis(new Axis())
                .setxAxis(new Axis())
                .setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions().setGroupPadding(0f).setPointPadding(0f).setPointPlacement(PointPlacement.BETWEEN)));
        options.setxAxis(new Axis().setTitle(new Title(xLabel)));
        options.setyAxis(new Axis().setTitle(new Title(yLabel)));
        options.getSingleXAxis().setLabels(new Labels().setFormat("{value}"+xAxisSuffix)).setType(AxisType.LINEAR);
        options.getSingleYAxis().setLabels(new Labels().setFormat("{value}"+yAxisSuffix)).setType(AxisType.LINEAR);
        for(Series<?> series : options.getSeries()) {
            series.setPointPadding(0f);
            series.setType(type);
            series.setPointPlacement(PointPlacement.ON);
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat(yFormatStr+" "+yLabel)
                    .setY(-5)
            );
        }
        if(min!=null) options.getSingleYAxis().setMin(min);
        if(max!=null&&max>min) options.getSingleYAxis().setMax(max);
    }
}

