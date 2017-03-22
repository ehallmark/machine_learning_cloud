package server.highcharts;

import analysis.SimilarPatentFinder;
import analysis.genetics.GeneticAlgorithm;
import analysis.genetics.lead_development.*;
import analysis.tech_tagger.SimilarityTechTagger;
import analysis.tech_tagger.TechTagger;
import analysis.tech_tagger.TechTaggerNormalizer;
import com.google.common.util.concurrent.AtomicDouble;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import server.SimilarPatentServer;
import tools.PortfolioList;
import value_estimation.Evaluator;
import value_estimation.SimilarityEvaluator;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 2/14/17.
 */
public class HighchartDataAdapter {
    private static final TechTagger tagger;
    private static final int NUM_MILLISECONDS_IN_A_DAY = 86400000;
    static {
        try {
            SimilarPatentServer.loadLookupTable();
        }catch(Exception e) {
            e.printStackTrace();
        }
        tagger = TechTaggerNormalizer.getDefaultTechTagger();
    }

    public static List<Series<?>> collectValueTimelineData(String company, Evaluator valueModel) {
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
        series.setName("AI Value");
        dates.forEach(date->{
            List<String> set = new ArrayList<>(dateToPatentMap.get(date));
            Collections.shuffle(set);
            Point point = new Point().setX(date.toEpochDay()*NUM_MILLISECONDS_IN_A_DAY);
            if(set!=null) {
                point.setY(set.stream().map(patent->valueModel.evaluate(patent)).limit(30).collect(Collectors.summingDouble(d->d)));
            } else {
                point.setY(0);
            }
            series.addPoint(point);
        });
        data.add(series);

        return data;
    }

    public static List<Series<?>> collectTechnologyTimelineData(String company) {
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
        // get pre tech from company
        tagger.getTechnologiesFor(Database.possibleNamesForAssignee(company), PortfolioList.Type.assignees,5).forEach(pair->{
            String tech = pair.getFirst();
            PointSeries series = new PointSeries();
            series.setName(tech);
            dates.forEach(date->{
                List<String> set = new ArrayList<>(dateToPatentMap.get(date));
                Collections.shuffle(set);
                Point point = new Point().setX(date.toEpochDay()*NUM_MILLISECONDS_IN_A_DAY);
                if(set!=null) {
                    point.setY(set.stream().map(patent->tagger.getTechnologyValueFor(patent, tech, PortfolioList.Type.patents)).limit(30).collect(Collectors.summingDouble(d->d)));
                } else {
                    point.setY(0);
                }
                series.addPoint(point);
            });
            data.add(series);
        });

        return data;
    }

    public static List<Series<?>> collectTechnologyData(String portfolio, PortfolioList.Type inputType, int limit) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(portfolio);
        tagger.getTechnologiesFor(Database.possibleNamesForAssignee(portfolio),inputType,limit).forEach(pair->{
            String tech = pair.getFirst();
            double prob = pair.getSecond();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });
        data.add(series);
        return data;
    }

    public static List<Series<?>> collectLikelyAssetBuyersData(String portfolio, PortfolioList.Type inputType, int limit, Evaluator buyerModel, WeightLookupTable<VocabWord> lookupTable) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(portfolio);
        ValueAttribute buyerAttr = new ValueAttribute("buyerValue",1.0,buyerModel);
        Evaluator similarityEvaluator = new SimilarityEvaluator(portfolio,lookupTable,new SimilarPatentFinder(Database.possibleNamesForAssignee(portfolio),portfolio,lookupTable).computeAvg());
        GeneticAlgorithm algorithm = new GeneticAlgorithm(new CompanySolutionCreator(Arrays.asList(new Attribute(portfolio,1.0) {
            @Override
            public Attribute dup() {
                return null;
            }

            @Override
            public double scoreAssignee(String assignee) {
                if(assignee.startsWith(portfolio)) return Double.MIN_VALUE;
                return similarityEvaluator.evaluate(assignee)*buyerAttr.scoreAssignee(assignee);
            }
        }),limit, (Runtime.getRuntime().availableProcessors()+1)/2),500,new CompanySolutionListener(),(Runtime.getRuntime().availableProcessors()+1)/2);
        algorithm.simulate(5,0.5,0.5);
        ((CompanySolution)(algorithm.getBestSolution())).getCompanyScores().forEach(e->{
            String tech = e.getKey();
            double prob = e.getValue();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });
        data.add(series);
        return data;
    }

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

    public static List<Series<?>> collectCompanyDetailsData(String company) {
        PointSeries series = new PointSeries();
        series.setName(company);
        // assets purchased
        series.addPoint(new Point("Assets Purchased",Database.getAssetsPurchasedCountFor(company)));
        series.addPoint(new Point("Assets Sold",Database.getAssetsSoldCountFor(company)));
        return Arrays.asList(series);
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
