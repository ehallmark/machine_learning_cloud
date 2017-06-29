package highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {
    protected Options options;
    protected String xLabel;
    protected String yLabel;
    protected SeriesType type;

    public Options getOptions() {
        return options;
    }


    protected void setupColumnAndBarAxes(String valueSuffix, int decimals) {
        options.setxAxis(new Axis().setTitle(new Title(xLabel)));
        options.setyAxis(new Axis().setTitle(new Title(yLabel)));
        options.getSingleXAxis().setType(AxisType.LINEAR);
        options.getSingleYAxis().setType(AxisType.LINEAR);
        options.getSingleXAxis().setLabels(new Labels().setFormat("{value}%"));
        options.getSingleYAxis().setLabels(new Labels().setFormat("{value}"+valueSuffix));
        for(Series<?> series : options.getSeries()) {
            series.setPointPadding(0f);
            series.setType(type);
            series.setPointPlacement(PointPlacement.ON);
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat("{point.y:."+decimals+"f}"+valueSuffix)
                    .setY(-5)
            );
        }
    }

    protected AbstractChart(String title, List<Series<?>> data, SeriesType type, int decimals, String valueSuffix, String xLabel, String yLabel) {
        System.out.println("Starting to build: "+type);
        String formatStr = "point.y:."+decimals+"f";
        this.yLabel=yLabel;
        this.xLabel=xLabel;
        this.type=type;
        options=new Options()
                .setChartOptions(new ChartOptions().setHeight(450).setType(type))
                .setTitle(new Title(title))
                .setTooltip(new Tooltip().setHeaderFormat("{point.x}% "+xLabel+" Range<br/>").setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> {point.name} <b> Total: {"+formatStr+"}"+valueSuffix+" "+yLabel+"</b><br/>"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setSeries(data)
                .setyAxis(new Axis())
                .setxAxis(new Axis())
                .setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions().setGroupPadding(0f).setPointPadding(0f).setPointPlacement(PointPlacement.ON)));
    }

    @Override
    public String toString() {
        return new JsonRenderer().toJson(options);
    }
}
