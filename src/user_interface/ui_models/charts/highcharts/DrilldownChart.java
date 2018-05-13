package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.color.HexColor;
import com.googlecode.wickedcharts.highcharts.options.color.HighchartsColor;
import com.googlecode.wickedcharts.highcharts.options.drilldown.DrilldownPoint;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.charts.aggregate_charts.AggregateLineChart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DrilldownChart extends Options {
    public static void createDrilldownChart(Options baseOptions, List<Pair<Number,PointSeries>> baseSeries) {
        PointSeries groupesSeries = new PointSeries();
        List<String> categories = new ArrayList<>(baseSeries.size());
        for(Pair<Number,PointSeries> seriesPair : baseSeries) {
            PointSeries series = seriesPair.getRight();
            groupesSeries.addPoint(new DrilldownPoint(baseOptions,createDrilldownOptions(baseOptions, series))
                    .setY(seriesPair.getFirst())//.setName(series.getName())
            );
            categories.add(series.getName());
        }
        if(baseOptions.getSingleXAxis()==null) {
            baseOptions.setxAxis(Collections.singletonList(new Axis()));
        }
        baseOptions.getSingleXAxis().setType(AxisType.CATEGORY).setCategories(categories);
        baseOptions.setSeries(Collections.singletonList(groupesSeries));
    }

    private static Options createDrilldownOptions(Options baseOptions, PointSeries series) {
        Options options = new Options();
        options.copyFrom(baseOptions);
        PointSeries newSeries = new PointSeries();
        newSeries.setName(series.getName());
        newSeries.setDataLabels(series.getDataLabels());
        List<String> categories = new ArrayList<>(series.getData().size());
        for(Point point : series.getData()) {
            categories.add(point.getName());
            newSeries.addPoint(new DrilldownPoint(options, baseOptions).setName(point.getName()));
        }
        options.setSeries(Collections.singletonList(newSeries));
        if(options.getSingleXAxis()==null) {
            options.setxAxis(Collections.singletonList(new Axis()));
        }
        options.getSingleXAxis().setCategories(categories).setType(AxisType.CATEGORY);
        return options;
    }
}