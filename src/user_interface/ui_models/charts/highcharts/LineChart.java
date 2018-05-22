package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;

import java.awt.*;
import java.util.Collections;

/**
 * Created by ehallmark on 2/14/17.
 */
public class LineChart extends AbstractChart {
    @Override
    public String getType() {
        return "line";
    }

    @Getter
    private boolean stockChart;

    public LineChart(Options _options, boolean stockChart, String title, String subTitle, String xAxisSuffix, String yAxisSuffix, String xLabel, String yLabel, int yDecimals) {
        this.stockChart=stockChart;
        String yFormatStr = "{point.y:."+yDecimals+"f}"+yAxisSuffix;
        String xFormatStr = "{point.key}"+xAxisSuffix;
        options=_options;
        options = options
                .setChartOptions(new ChartOptions().setHeight(450).setType(SeriesType.LINE))
                .setTitle(new Title(title))
                .setLegend(new Legend(true).setAlign(HorizontalAlignment.CENTER).setLayout(LegendLayout.HORIZONTAL).setVerticalAlign(VerticalAlignment.BOTTOM))
                .setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions()))
                .setExporting(new ExportingOptions().setEnabled(true))
                .setTooltip(new Tooltip().setEnabled(true).setShared(false).setUseHTML(true)
                        .setHeaderFormat("<small>"+xFormatStr+"</small><table>")
                        .setPointFormat("<tr><td><span style=\"color:{point.color}\">\u25CF</span> {series.name}</td><td> <b> "+yFormatStr+" "+yLabel+"</b></td></tr>")
                        .setFooterFormat("</table>")
                )
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"));
        if(subTitle!=null) options.setSubtitle(new Title(subTitle));
        if(isStockChart()) {
            options.setxAxis(new Axis().setType(AxisType.LINEAR).setTickInterval(1f).setTitle(new Title(xLabel)));
        } else {
            if(options.getSingleXAxis()==null) {
                options.setxAxis(Collections.singletonList(new Axis()));
            }
            options.getSingleXAxis().setTitle(new Title(xLabel));
        }
        options.setyAxis(new Axis().setType(AxisType.LINEAR).setMin(0).setTitle(new Title(ColumnChart.capitalize(yLabel)+" Count")));
        if(options.getSeries().size()==1) {
            for (Series<?> series : options.getSeries()) {
                series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat("{y}")
                    .setY(-5)
                );
            }
        }

    }
    
}

