package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import org.nd4j.linalg.primitives.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DrilldownChart {
    public static Options createDrilldownChart(boolean isHistogram, boolean swapAxis, Options baseOptions, List<Pair<Number,ArraySeries>> baseSeries) {
        PointSeries groupesSeries = new PointSeries();
        if(isHistogram) {
            groupesSeries.setShowInLegend(false);
        }
        AtomicInteger inc = new AtomicInteger(0);
        List<DrilldownPointSeries> drilldownSeries = new ArrayList<>(baseSeries.size());
        DrilldownOptions drilldownOptions = new DrilldownOptions();
        drilldownOptions.copyFrom(baseOptions);
        for(Pair<Number,ArraySeries> seriesPair : baseSeries) {
            String id = "drilldown"+String.valueOf(inc.getAndIncrement());
            ArraySeries series = seriesPair.getRight();
            String seriesName = series.getName();
            groupesSeries.addPoint(new DrilldownParentPoint(seriesName, seriesPair.getFirst(),id));
            drilldownSeries.add(createDrilldownSeries(series, id, swapAxis, false));
        }
        drilldownOptions.setDrilldownData(drilldownSeries);
        drilldownOptions.setSeries(Collections.singletonList(groupesSeries));
        return drilldownOptions;
    }

    private static DrilldownPointSeries createDrilldownSeries(ArraySeries series, String id, boolean colorByPoint, boolean showInLegend) {
        DrilldownPointSeries newSeries = new DrilldownPointSeries();
        newSeries.setId(id);
        newSeries.setShowInLegend(showInLegend);
        newSeries.setColorByPoint(colorByPoint);
        newSeries.setData(series.getData());
        return newSeries;
    }
}