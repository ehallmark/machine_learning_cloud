package server.highcharts;

import com.googlecode.wickedcharts.highcharts.jackson.JsonRenderer;
import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public abstract class AbstractChart {
    protected Options options;

    public Options getOptions() {
        return options;
    }

    protected void setupAxes() {
        options.setxAxis(new Axis());
        options.setyAxis(new Axis());
        stripAxis(options.getSingleXAxis());
        stripAxis(options.getSingleYAxis());
        options.getSeries().forEach(series->{
            series.setDataLabels(new DataLabels(true)
                    .setRotation(-90)
                    .setColor(Color.white)
                    .setAlign(HorizontalAlignment.RIGHT)
                    .setFormat("{point.y:.1f}")
                    .setY(-20)
            );
        });
    }

    protected static void stripAxis(Axis axis) {
        axis
                .setTitle(new Title(""))
                .setLineWidth(0)
                .setMinorGridLineWidth(0)
                .setLabels(new Labels().setEnabled(false))
                .setMinorTickWidth(0)
                .setTickLength(0);
    }

    protected AbstractChart(String title, List<Series<?>> data, SeriesType type) {
        options=new Options()
                .setChartOptions(new ChartOptions().setType(type))
                .setTitle(new Title(title))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setSeries(data);
    }

    @Override
    public String toString() {
        return new JsonRenderer().toJson(options).toString();
    }
}
