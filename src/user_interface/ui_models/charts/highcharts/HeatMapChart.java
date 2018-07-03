package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;
import user_interface.ui_models.charts.aggregations.Type;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class HeatMapChart extends AbstractChart {
    @Override
    public String getType() {
        return "heatmap";
    }

    public HeatMapChart(Options _options, String title, String subTitle, String xAxisSuffix, String yAxisSuffix, String xLabel, String yLabel, int yDecimals, Type collectorType, List<String> xCategories, List<String> yCategories) {
        String yFormatStr = "{point.y:."+yDecimals+"f}"+yAxisSuffix;
        String xFormatStr = "{point.key}"+xAxisSuffix;
        options=_options;
        options = options
                .setChartOptions(new ChartOptions().setHeight(450).setType(SeriesType.HEATMAP))
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
        if(options.getSingleXAxis()==null) {
            options.setxAxis(Collections.singletonList(new Axis().setType(AxisType.CATEGORY)));
        }
        if(options.getSingleYAxis()==null) {
            options.setyAxis(Collections.singletonList(new Axis().setType(AxisType.CATEGORY)));
        }
        options.getSingleXAxis().setTitle(new Title(xLabel)).setCategories(xCategories);
        options.getSingleYAxis().setTitle(new Title(ColumnChart.capitalize(yLabel)+" "+collectorType)).setCategories(yCategories);
        ArraySeries combinedSeries = new ArraySeries();
        combinedSeries.setDataLabels(new DataLabels(true)
                .setRotation(0)
                .setColor(Color.black)
                .setAlign(HorizontalAlignment.CENTER)
                .setFormat("{y}")
                .setY(-5)
        );
        for (int i = 0; i < options.getSeries().size(); i++) {
            ArraySeries arraySeries = (ArraySeries)options.getSeries().get(i);
            for(List point : arraySeries.getData()) {
                List<Object> newData = new ArrayList<>(3);
                newData.add(i);
                newData.add(point.get(0));
                newData.add(point.get(1));
                combinedSeries.addPoint(newData);
            }
        }
        options.setSeries(Collections.singletonList(combinedSeries));
    }
    
}

