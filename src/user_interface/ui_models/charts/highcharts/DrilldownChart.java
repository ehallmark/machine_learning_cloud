package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.color.HexColor;
import com.googlecode.wickedcharts.highcharts.options.color.HighchartsColor;
import com.googlecode.wickedcharts.highcharts.options.drilldown.DrilldownPoint;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.charts.aggregate_charts.AggregateLineChart;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DrilldownChart extends Options {
    public static void createDrilldownChart(Options baseOptions, List<Pair<Number,PointSeries>> baseSeries, boolean createCategories) {
        PointSeries groupesSeries = new PointSeries();
        for(Pair<Number,PointSeries> seriesPair : baseSeries) {
            PointSeries series = seriesPair.getRight();
            series.addPoint(new DrilldownPoint(baseOptions,createDrilldownOptions(baseOptions, series, createCategories))
                    .setY(seriesPair.getFirst())
            );
        }
        baseOptions.setSeries(Collections.singletonList(groupesSeries));
    }

    private static Options createDrilldownOptions(Options baseOptions, PointSeries series, boolean createCategories) {
        Options options = new Options();
        options.copyFrom(baseOptions);
        PointSeries newSeries = new PointSeries();
        newSeries.setName(series.getName());
        newSeries.setDataLabels(series.getDataLabels());
        for(Point point : series.getData()) {
            newSeries.addPoint(new DrilldownPoint(options, baseOptions).setY(point.getY()).setName(point.getName()));
        }
        options.setSeries(Collections.singletonList(newSeries));
        if(createCategories) {
            List<String> categories = newSeries.getData().isEmpty() ? Collections.singletonList("0")
                    : newSeries.getData().stream().map(p -> ((Point) p).getName()).collect(Collectors.toList());
            System.out.println("Categories for timeline: " + String.join(", ", categories));
            if(options.getSingleXAxis()==null) {
                options.setxAxis(Collections.singletonList(new Axis()));
            }
            options.getSingleXAxis().setCategories(categories).setType(AxisType.CATEGORY);
        }
        return options;
    }
}