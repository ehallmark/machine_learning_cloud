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
    protected SeriesType type;

    public Options getOptions() {
        return options;
    }


    protected void setupColumnAndBarAxes(String valueSuffix, int decimals) {
        options.setxAxis(new Axis());
        options.setyAxis(new Axis());
        stripAxis(options.getSingleXAxis());
        stripAxis(options.getSingleYAxis());
        options.getSingleXAxis().setType(AxisType.LINEAR);
        options.getSingleYAxis().setType(AxisType.LINEAR);
        options.getSingleXAxis().setLabels(new Labels().setFormat("{value}%"));
        options.getSingleYAxis().setLabels(new Labels().setFormat("{value}"+valueSuffix));
        for(Series<?> series : options.getSeries()) {
            series.setPointPadding(0f);
            series.setType(type);
            series.setPointPlacement(PointPlacement.BETWEEN);
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat("{point.y:."+decimals+"f}"+valueSuffix)
                    .setY(-5)
            );
        }
    }

    protected static void stripAxis(Axis axis) {
        axis
                .setTitle(new Title(""));
                //.setLineWidth(0)
                //.setMinorGridLineWidth(0)
                //.setLabels(new Labels().setEnabled(false))
                //.setMinorTickWidth(0)
                //.setGridLineWidth(0)
                //.setTickWidth(0);
    }

    protected AbstractChart(String title, List<Series<?>> data, SeriesType type, int decimals, String valueSuffix) {
        System.out.println("Starting to build: "+type);
        String formatStr = "point.y:."+decimals+"f";
        this.type=type;
        options=new Options()
                .setChartOptions(new ChartOptions().setType(type))
                .setTitle(new Title(title))
                .setTooltip(new Tooltip().setHeaderFormat("<b>{point.x} - {point.x+20}</b><br/>").setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> {point.name}: <b> {"+formatStr+"}"+valueSuffix+"</b><br/>"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setSeries(data)
                .setyAxis(new Axis())
                .setxAxis(new Axis())
                .setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions().setGroupPadding(0f).setPointPadding(0f).setPointPlacement(PointPlacement.BETWEEN)));
    }

    @Override
    public String toString() {
        return new JsonRenderer().toJson(options);
    }
}
