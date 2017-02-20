package server.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.functions.RedirectFunction;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.apache.commons.lang3.StringEscapeUtils;

import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {
    protected Options options;

    public Options getOptions() {
        return options;
    }

    protected void setupColumnAndBarAxes(String valueSuffix) {
        options.setxAxis(new Axis());
        options.setyAxis(new Axis());
        stripAxis(options.getSingleXAxis());
        stripAxis(options.getSingleYAxis());
        options.getSingleXAxis().setType(AxisType.CATEGORY);
        options.getSingleYAxis().setType(AxisType.LINEAR);
        options.getSingleYAxis().setLabels(new Labels().setFormat("{value}"+valueSuffix));
        options.getSeries().forEach(series->{
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat("{point.y:.1f}"+valueSuffix)
                    .setY(-5)
            );
        });
        options.setTooltip(new Tooltip().setValueSuffix(valueSuffix));
    }

    protected static void stripAxis(Axis axis) {
        axis
                .setTitle(new Title(""))
                .setLineWidth(0)
                .setMinorGridLineWidth(0)
                //.setLabels(new Labels().setEnabled(false))
                .setMinorTickWidth(0)
                //.setGridLineWidth(0)
                .setTickWidth(0);
    }

    protected AbstractChart(String title, List<Series<?>> data, SeriesType type) {
        options=new Options()
                .setChartOptions(new ChartOptions().setType(type))
                .setTitle(new Title(title))
                .setTooltip(new Tooltip().setPointFormat("{point.y:.2f}"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setSeries(data)
                .setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions()
                        .setPoint(new PointOptions().setEvents(new Events()))));
   }

    @Override
    public String toString() {
        return new JsonRenderer().toJson(options);
    }
}
