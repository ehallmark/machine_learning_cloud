package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;

/**
 * Created by ehallmark on 2/14/17.
 */
public class WordCloudChart extends AbstractChart {
    @Override
    public String getType() {
        return "wordcloud";
    }

    public WordCloudChart(Options _options, String title, String subTitle, String yLabel) {
        System.out.println("Starting to build: word cloud");
        options=_options;
        options = options
                .setExporting(new ExportingOptions().setEnabled(true))
                .setChartOptions(new ChartOptions())
                .setTitle(new Title(title))
                .setSubtitle(new Title(subTitle))
                .setCredits(new CreditOptions().setEnabled(true).setText("GTT Group").setHref("http://www.gttgrp.com"));
        if(options.getSeries().size()>0) {
            options.getSeries().get(0).setName(yLabel);
        }
    }
}
