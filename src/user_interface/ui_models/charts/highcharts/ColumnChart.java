package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class ColumnChart extends AbstractChart {
    @Override
    public String getType() {
        return "column";
    }

    public ColumnChart(String title, List<Series<?>> data, Double min, Double max, String xAxisSuffix, String yAxisSuffix, String xLabel, String yLabel, int yDecimals, List<String> categories) {
        String yFormatStr = "{point.y:."+yDecimals+"f}"+yAxisSuffix;
        String xFormatStr = "{point.name}"+xAxisSuffix;
        SeriesType type = SeriesType.COLUMN;
        options=new Options()
                .setChartOptions(new ChartOptions().setHeight(450).setType(type))
                .setTitle(new Title(title))
                .setTooltip(new Tooltip().setEnabled(true).setHeaderFormat(xFormatStr+"<br/>").setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> <b> Count: "+yFormatStr+" "+yLabel+"</b><br/>"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setSeries(data)
                .setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions().setGroupPadding(0f).setPointPadding(0f).setPointPlacement(PointPlacement.ON)));
        options.setxAxis(new Axis().setTitle(new Title(xLabel)).setMin(-0.5).setMax(-0.5+categories.size()).setShowFirstLabel(true).setShowLastLabel(true).setStartOnTick(false).setEndOnTick(false).setTickmarkPlacement(TickmarkPlacement.BETWEEN).setCategories(categories));
        options.setyAxis(new Axis().setTitle(new Title(yLabel)));
        options.getSingleXAxis().setLabels(new Labels().setFormat("{value}"+xAxisSuffix)).setType(AxisType.CATEGORY);
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

