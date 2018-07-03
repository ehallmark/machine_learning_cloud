package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.color.ColorReference;
import com.googlecode.wickedcharts.highcharts.options.color.HexColor;
import com.googlecode.wickedcharts.highcharts.options.color.HighchartsColor;
import com.googlecode.wickedcharts.highcharts.options.color.RgbaColor;
import com.googlecode.wickedcharts.highcharts.options.heatmap.ColorAxis;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import lombok.Getter;
import user_interface.ui_models.charts.aggregations.Type;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 2/14/17.
 */
public class HeatMapChart extends AbstractChart {
    @Override
    public String getType() {
        return "heatmap";
    }

    public HeatMapChart(Options _options, String title, String subTitle, String xAxisSuffix, String yAxisSuffix, String xLabel, String yLabel, String valueSuffix, int valueDecimals, Type collectorType, List<String> xCategories, List<String> yCategories) {
        String valueFormatStr = "{point.value:."+valueDecimals+"f}"+valueSuffix;
        String yFormatStr = "{point.y}"+yAxisSuffix;
        String xFormatStr = "{point.x}"+xAxisSuffix;
        options=_options;
        int[] color = getColor(0, 0);
        ColorReference maxColor = new RgbaColor(color[0],color[1],color[2], 1f);
        options = options
                .setColorAxis(new ColorAxis().setMin(0).setMinColor(new HexColor("#FFFFFF")).setMaxColor(maxColor))
                .setChartOptions(new ChartOptions().setHeight(550).setType(SeriesType.HEATMAP))
                .setTitle(new Title(title))
                .setLegend(new Legend(true).setAlign(HorizontalAlignment.RIGHT).setLayout(LegendLayout.VERTICAL).setVerticalAlign(VerticalAlignment.BOTTOM).setY(-25).setSymbolHeight(280))
                .setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions()))
                .setExporting(new ExportingOptions().setEnabled(true))
                //.setTooltip(new Tooltip().setEnabled(true).setShared(false).setUseHTML(true)
                //        .setHeaderFormat("<small>"+yFormatStr+"</small><table>")
                //        .setPointFormat("<tr><td><span style=\"color:{point.color}\">\u25CF</span>"+xFormatStr+"</td><td> <b> "+valueFormatStr+" "+valueSuffix+"</b></td></tr>")
                //        .setFooterFormat("</table>")
                //)
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
                .setFormat("{value:.\"+valueDecimals+\"f}"+valueSuffix)
        );
        combinedSeries.setBorderWidth(1);
        for (int i = 0; i < options.getSeries().size(); i++) {
            ArraySeries arraySeries = (ArraySeries)options.getSeries().get(i);
            Map<String,Number> dataMap = arraySeries.getData().stream().collect(Collectors.toMap(d->(String)d.get(0),d->(Number)d.get(1)));
            for(int j = 0; j < xCategories.size(); j++) {
                String x = xCategories.get(j);
                List<Object> newData = new ArrayList<>(3);
                newData.add(j);
                newData.add(i);
                newData.add(dataMap.getOrDefault(x, 0));
                combinedSeries.addPoint(newData);
            }
        }
        options.setSeries(Collections.singletonList(combinedSeries));
    }
    
}

