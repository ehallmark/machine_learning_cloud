package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import user_interface.ui_models.charts.aggregations.Type;

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

    public ColumnChart(Options _options, String title, Double min, Double max, String xAxisSuffix, String yAxisSuffix, String xLabel, String yLabel, String subTitle, int yDecimals, List<String> categories, Type collectorType) {
        String yFormatStr = "{point.y:."+yDecimals+"f}"+yAxisSuffix;
        SeriesType type = SeriesType.COLUMN;
        options=_options;
        String xFormatStr = options.getSeries().size()>1 ? "{series.name}" : ("{point.key}"+xAxisSuffix);
        options = options
                .setChartOptions(new ChartOptions().setHeight(450).setType(type))
                .setTitle(new Title(title))
                //.setLegend(new Legend(true).setAlign(HorizontalAlignment.CENTER).setLayout(LegendLayout.HORIZONTAL).setVerticalAlign(VerticalAlignment.BOTTOM))
                .setExporting(new ExportingOptions().setEnabled(true))
                .setTooltip(new Tooltip().setEnabled(true)
                        .setHeaderFormat(xFormatStr+"<br/>")
                        .setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> <b> "+collectorType+": "+yFormatStr+" "+yLabel+"</b><br/>"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setPlotOptions(new PlotOptionsChoice().setSeries(
                        new PlotOptions()
                                .setGroupPadding(0f)
                                .setPointPadding(0f)
                                .setPointPlacement(null)
                ));
        if(subTitle!=null) options.setSubtitle(new Title(subTitle));
        options.setxAxis(new Axis().setTitle(new Title(xLabel))
                .setStartOnTick(false)
                .setEndOnTick(false)
                .setTickmarkPlacement(TickmarkPlacement.ON)
                .setShowFirstLabel(true)
                .setShowLastLabel(true));
        options.setyAxis(new Axis().setTitle(new Title(capitalize(yLabel))));
        options.getSingleXAxis().setLabels(new Labels().setFormat("{value}"+xAxisSuffix).setAlign(HorizontalAlignment.CENTER).setRotation(0)).setType(AxisType.CATEGORY);
        options.getSingleYAxis().setLabels(new Labels().setFormat("{value}"+yAxisSuffix)).setType(AxisType.LINEAR);
        for(Series<?> series : options.getSeries()) {
            series.setPointPadding(0f);
            series.setType(type);
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat(yFormatStr)
                    .setY(-5)
            );
        }
        if(min!=null) options.getSingleYAxis().setMin(min);
        if(max!=null&&min!=null&&max>min) options.getSingleYAxis().setMax(max);
    }

    static String capitalize(String str) {
        if(str==null||str.isEmpty()) return str;
        return str.substring(0,1).toUpperCase()+str.substring(1);
    }
}

