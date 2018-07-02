package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.color.*;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import user_interface.ui_models.charts.aggregations.Type;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by ehallmark on 2/14/17.
 */
public class PieChart extends AbstractChart {
    @Override
    public String getType() {
        return "pie";
    }

    public PieChart(Options _options, String title, String subTitle, String yLabel, Type collectorType, boolean applyPieChartCenterFill) {
        SeriesType type = SeriesType.PIE;
        System.out.println("Starting to build: "+type);
        options=_options;
        options = options
                .setExporting(new ExportingOptions().setEnabled(true))
                .setChartOptions(new ChartOptions().setHeight(450).setType(type))
                .setTitle(new Title(title))
                .setSubtitle(new Title(subTitle))
                .setTooltip(new Tooltip().setPointFormat("<span style=\"color:{point.color}\">\u25CF</span> {point.name}:<b> {point.percentage:.1f}%</b><br/>"+collectorType+": <b> {point.y} "+yLabel+"</b><br/>"))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"))
                .setyAxis(new Axis());
        if(applyPieChartCenterFill) {
            // add coloring
            List<int[]> colors = RGB_COLORS;
            List<ColorReference> colorReferences = new ArrayList<>(colors.size());
            for (int[] color : colors) {
                ColorReference colorRef = radialColorReference(color);
                colorReferences.add(colorRef);
            }
            for(Series series : options.getSeries()) {
                series.setInnerSize(new PixelOrPercent(55, PixelOrPercent.Unit.PERCENT));
                series.setSize(new PixelOrPercent(80, PixelOrPercent.Unit.PERCENT));
            }
            options.setColors(colorReferences);
        }
        for(Series<?> series : options.getSeries()) {
            if(series.getDataLabels()==null) {
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
}
