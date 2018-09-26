package user_interface.ui_models.charts.highcharts;

import com.googlecode.wickedcharts.highcharts.options.AxisType;
import com.googlecode.wickedcharts.highcharts.options.Options;
import com.googlecode.wickedcharts.highcharts.options.color.ColorReference;
import com.googlecode.wickedcharts.highcharts.options.color.RgbaColor;
import org.nd4j.linalg.primitives.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DrilldownChart {
    public static Options createDrilldownChart(boolean isHistogram, boolean swapAxis, Options baseOptions, List<Pair<Number,ArraySeries>> baseSeries) {
        ExtendedPointSeries groupesSeries = new ExtendedPointSeries();
        groupesSeries.setShowInLegend(false);
        boolean colorGroupBySeriesByPoint = !isHistogram || !swapAxis;
        boolean colorPointSeriesByPoint = !isHistogram || swapAxis;
        groupesSeries.setColorByPoint(colorGroupBySeriesByPoint);

        AtomicInteger inc = new AtomicInteger(0);
        List<DrilldownPointSeries> drilldownSeries = new ArrayList<>(baseSeries.size());
        DrilldownOptions drilldownOptions = new DrilldownOptions();
        drilldownOptions.copyFrom(baseOptions);
        List<ColorReference> outerColors = new ArrayList<>();
        int i = 0;
        List<String> categories = new ArrayList<>();
        for(Pair<Number,ArraySeries> seriesPair : baseSeries) {
            int[] color = AbstractChart.getColor(i, 0);
            if(colorGroupBySeriesByPoint) {
                if (isHistogram) {
                    outerColors.add(new RgbaColor(color[0], color[1], color[2], 1f));
                } else {
                    outerColors.add(AbstractChart.radialColorReference(color));
                }
            }
            String id = "drilldown"+String.valueOf(inc.getAndIncrement());
            ArraySeries series = seriesPair.getRight();
            String seriesName = series.getName();
            groupesSeries.addPoint(new DrilldownParentPoint(seriesName, seriesPair.getFirst(),id));
            categories.add(seriesName);
            drilldownSeries.add(createDrilldownSeries(color, series, id, colorPointSeriesByPoint, isHistogram));
            i++;
        }
        if(colorGroupBySeriesByPoint) {
            groupesSeries.setColors(outerColors);
        }
        drilldownOptions.setDrilldownData(drilldownSeries);
        drilldownOptions.setSeries(Collections.singletonList(groupesSeries));
        if(isHistogram) {
            drilldownOptions.getSingleXAxis().setCategories(categories).setType(AxisType.CATEGORY);
        }
        return drilldownOptions;
    }

    private static DrilldownPointSeries createDrilldownSeries(int[] color, ArraySeries series, String id, boolean colorByPoint, boolean isHistogram) {
        DrilldownPointSeries newSeries = new DrilldownPointSeries();
        newSeries.setId(id);
        newSeries.setShowInLegend(false);
        newSeries.setColorByPoint(colorByPoint);
        newSeries.setData(series.getData());
        if(colorByPoint) {
            List<ColorReference> colors = new ArrayList<>();
            for (int i = 0; i < series.getData().size(); i++) {
                ColorReference colorReference;
                if(isHistogram) {
                    int[] pointColor = AbstractChart.getColor(i, 0);
                    colorReference = new RgbaColor(pointColor[0], pointColor[1], pointColor[2], 1f);
                } else {
                    int[] pointColor = AbstractChart.brighten(color[0], color[1], color[2], Math.min(90, i * 10));
                    colorReference = AbstractChart.radialColorReference(pointColor);
                }
                colors.add(colorReference);
            }
            newSeries.setColors(colors);
        } else {
            newSeries.setColor(new RgbaColor(color[0], color[1], color[2], 1f));
        }
        return newSeries;
    }
}