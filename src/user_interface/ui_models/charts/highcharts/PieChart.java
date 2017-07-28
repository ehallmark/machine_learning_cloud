package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;

import java.awt.*;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class PieChart extends AbstractChart {
    @Override
    public String getType() {
        return "pie";
    }

    public PieChart(String title, String subTitle, List<Series<?>> data, String yLabel) {
        SeriesType type = SeriesType.PIE;
        System.out.println("Starting to build: "+type);
        options=new Options()
                .setExporting(new ExportingOptions().setEnabled(true))
                .setChartOptions(new ChartOptions().setHeight(450).setType(type))
                .setTitle(new Title(title))
                .setSubtitle(new Title(subTitle))
                .setTooltip(new Tooltip().setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> {point.name}:<b> {point.percentage:.1f}%</b><br/>Count: <b> {point.y} "+yLabel+"</b><br/>"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setSeries(data)
                .setyAxis(new Axis())
                .setxAxis(new Axis())
                .setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions().setGroupPadding(0f).setPointPadding(0f).setPointPlacement(PointPlacement.ON)));
        for(Series<?> series : options.getSeries()) {
            series.setDataLabels(new DataLabels(true)
                    .setRotation(0)
                    .setColor(Color.black)
                    .setAlign(HorizontalAlignment.CENTER)
                    .setFormat("<b>{point.name}</b>: {point.percentage:.1f}%")
                    .setY(-5)
            );
        }
    }
}
