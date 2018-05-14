package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import org.nd4j.linalg.primitives.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DrilldownChart {
    public static Options createDrilldownChart(Options baseOptions, List<Pair<Number,ArraySeries>> baseSeries) {
        PointSeries groupesSeries = new PointSeries();
        AtomicInteger inc = new AtomicInteger(0);
        List<DrilldownPointSeries> drilldownSeries = new ArrayList<>(baseSeries.size());
        DrilldownOptions drilldownOptions = new DrilldownOptions();
        drilldownOptions.copyFrom(baseOptions);
        for(Pair<Number,ArraySeries> seriesPair : baseSeries) {
            String id = "drilldown"+String.valueOf(inc.getAndIncrement());
            ArraySeries series = seriesPair.getRight();
            groupesSeries.addPoint(new DrilldownParentPoint(series.getName(), seriesPair.getFirst(),id));
            drilldownSeries.add(createDrilldownSeries(series, id));
        }
        drilldownOptions.setDrilldownData(drilldownSeries);
        drilldownOptions.setSeries(Collections.singletonList(groupesSeries));
        return drilldownOptions;
    }

    private static DrilldownPointSeries createDrilldownSeries(ArraySeries series, String id) {
        DrilldownPointSeries newSeries = new DrilldownPointSeries();
        newSeries.setId(id);
        newSeries.setData(series.getData());
        return newSeries;
    }
}