package server.highcharts;

import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import com.googlecode.wickedcharts.highcharts.options.series.SimpleSeries;
import seeding.Database;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 2/14/17.
 */
public class HighchartDataAdapter {
    private static final int NUM_MILLISECONDS_IN_A_DAY = 86400000;
    public static List<Series<?>> collectCompanyActivityData(String company) {
        List<Series<?>> data = new ArrayList<>();

        Collection<String> patents = Database.selectPatentNumbersFromAssignee(company);
        Map<LocalDate,Set<String>> dateToPatentMap = new HashMap<>();
        patents.forEach(patent->{
            LocalDate date = Database.getPubDateFor(patent);
            if(date!=null) {
                // groups by month
                date = date.withDayOfMonth(1);
                if(dateToPatentMap.containsKey(date)) {
                    dateToPatentMap.get(date).add(patent);
                } else {
                    Set<String> set = new HashSet<>();
                    set.add(patent);
                    dateToPatentMap.put(date,set);
                }
            }
        });

        List<LocalDate> dates = new ArrayList<>(dateToPatentMap.keySet());
        if (dates.isEmpty()) return new ArrayList<>();
        LocalDate min = Collections.min(dates);
        LocalDate max = Collections.max(dates);
        dates.clear();

        {
            LocalDate date = min;
            while (date.isBefore(max) || date.isEqual(max)) {
                dates.add(date);
                date = date.plusMonths(1);
            }
        }
        PointSeries series = new PointSeries();
        dates.forEach(date->{
            Point point = new Point()
                    .setX(date.toEpochDay()*NUM_MILLISECONDS_IN_A_DAY)
                    .setY(dateToPatentMap.containsKey(date)?dateToPatentMap.get(date).size():0);
             series.addPoint(point);
        });

        data.add(series);

        return data;
    }
}
