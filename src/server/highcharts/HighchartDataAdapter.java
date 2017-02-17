package server.highcharts;

import com.google.common.util.concurrent.AtomicDouble;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import seeding.Database;
import tools.PortfolioList;
import value_estimation.Evaluator;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 2/14/17.
 */
public class HighchartDataAdapter {
    private static final int NUM_MILLISECONDS_IN_A_DAY = 86400000;

    public static List<Series<?>> collectData(String name, PortfolioList portfolioList, boolean similarity) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(name);
        portfolioList.getPortfolio().forEach(p->{
            Point point = new Point(p.getName(),similarity?(p.getSimilarity()*100):p.getAvgValue()); // for visualizing percentages
            series.addPoint(point);
        });
        data.add(series);
        return data;
    }

    public static List<Series<?>> collectSimilarityData(String name, PortfolioList portfolioList) {
        return collectData(name,portfolioList,true);
    }

    public static List<Series<?>> collectValueData(String name, PortfolioList portfolioList) {
        return collectData(name,portfolioList,false);
    }


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
        series.setName(company);
        dates.forEach(date->{
            Point point = new Point()
                    .setX(date.toEpochDay()*NUM_MILLISECONDS_IN_A_DAY)
                    .setY(dateToPatentMap.containsKey(date)?dateToPatentMap.get(date).size():0);
             series.addPoint(point);
        });

        data.add(series);

        return data;
    }

    public static List<Series<?>> collectAverageValueData(String portfolio, PortfolioList.Type inputType, List<Evaluator> evaluators) {
        // Weighted avg by portfolio size
        List<Series<?>> data = new ArrayList<>(1);
        Collection<String> collection = inputType.equals(PortfolioList.Type.assignees) ?
                Database.possibleNamesForAssignee(portfolio) : new HashSet<>(Arrays.asList(portfolio));
        if(collection.isEmpty()) return Collections.emptyList();
        PointSeries series = new PointSeries();
        evaluators.forEach(evaluator->{
            series.setName(portfolio);
            AtomicDouble value = new AtomicDouble(0.0);
            AtomicInteger totalSize = new AtomicInteger(0);
            collection.forEach(c->{
                int size = Math.max(1,Database.getExactAssetCountFor(c));
                totalSize.addAndGet(size);
                value.addAndGet(evaluator.evaluate(c)*size);
            });
            Point point = new Point(evaluator.getModelName(),value.get()/totalSize.get());
            series.addPoint(point);
        });
        data.add(series);
        return data;
    }
}
