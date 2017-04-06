package server.highcharts;

import analysis.SimilarPatentFinder;
import analysis.WordFrequencyPair;
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
import org.nd4j.linalg.api.ops.impl.transforms.SoftMax;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import server.SimilarPatentServer;
import tools.MinHeap;
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

    public static List<Series<?>> collectValueTimelineData(String assignee, Evaluator valueModel) {
        return collectValueTimelineData(Database.selectPatentNumbersFromAssignee(assignee),valueModel);
    }
    public static List<Series<?>> collectValueTimelineData(Collection<String> patents, Evaluator valueModel) {
        List<Series<?>> data = new ArrayList<>();

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
                point.setY(set.stream().limit(100).map(patent->valueModel.evaluate(patent)).collect(Collectors.averagingDouble(d->d)));
            } else {
                point.setY(0);
            }
            series.addPoint(point);
        });
        data.add(series);

        return data;
    }

    public static List<Series<?>> collectTechnologyTimelineData(String company) {
        return collectTechnologyTimelineData(Database.selectPatentNumbersFromAssignee(company));
    }
    public static List<Series<?>> collectTechnologyTimelineData(Collection<String> patents) {
        List<Series<?>> data = new ArrayList<>();
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
        if(dates.size()==0) {
            return Collections.emptyList();
        }

        final int sampleSize = 100;
        Random rand = new Random(69);
        Map<String,Double> techScores = new HashMap<>();
        List<String> patentList = new ArrayList<>(patents);
        List<String> patentSample = new ArrayList<>(sampleSize);
        while(patentList.size()>0&&patentSample.size()<sampleSize) {
            patentSample.add(patentList.remove(rand.nextInt(patentList.size())));
        }
        // get pre tech from company
        tagger.getTechnologiesFor(patentSample, PortfolioList.Type.patents,100).forEach(pair->{
            String tech = pair.getFirst();
            PointSeries series = new PointSeries();
            series.setName(tech);
            dates.forEach(date->{
                List<String> set = new ArrayList<>(dateToPatentMap.get(date));
                Collections.shuffle(set);
                Point point = new Point().setX(date.toEpochDay()*NUM_MILLISECONDS_IN_A_DAY);
                if(set!=null) {
                    point.setY(tagger.getTechnologyValueFor(set.stream().limit(100).collect(Collectors.toList()), tech, PortfolioList.Type.patents));
                } else {
                    point.setY(0d);
                }
                series.addPoint(point);
            });
            data.add(series);
            techScores.put(tech,series.getData().stream().collect(Collectors.averagingDouble(p->p.getY().doubleValue())));
        });

        Comparator<Series<?>> comp = (s1,s2) -> (techScores.get(s2.getName()).compareTo(techScores.get(s1.getName())));
        List<Series<?>> cleanData = data.stream().sorted(comp).limit(10).collect(Collectors.toList());
        applySoftMaxToTimeSeries(cleanData);
        return cleanData;
    }

    private static void applySoftMaxToTimeSeries(List<Series<?>> cleanData) {
        int dataPoints = cleanData.stream().map(series->series.getData().size()).max(Integer::compareTo).get();
        int numSeries = cleanData.size();
        INDArray allValues = Nd4j.create(dataPoints,numSeries);
        for(int i = 0; i < numSeries; i++) {
            Series<?> series = cleanData.get(i);
            List<Point> seriesList = ((PointSeries)series).getData();
            for(int j = 0; j < dataPoints; j++) {
                double val;
                if(seriesList.size()>j) {
                    val=seriesList.get(j).getY().doubleValue();
                } else {
                    val=0d;
                }
                allValues.putScalar(j, i, val);
            }
        }
        INDArray softmax = softMax(allValues);
        for(int i = 0; i < cleanData.size(); i++) {
            Series<?> series = cleanData.get(i);
            List<Point> seriesList = ((PointSeries)series).getData();
            for(int j = 0; j < seriesList.size(); j++) {
                seriesList.get(j).setY(softmax.getDouble(j,i));
            }
        }
    }

    private static void applySoftMax(List<Series<?>> cleanData) {
        int dataPoints = cleanData.stream().map(series->series.getData().size()).max(Integer::compareTo).get();
        int numSeries = cleanData.size();
        INDArray allValues = Nd4j.create(dataPoints,numSeries);
        for(int i = 0; i < numSeries; i++) {
            Series<?> series = cleanData.get(i);
            List<Point> seriesList = ((PointSeries)series).getData();
            for(int j = 0; j < dataPoints; j++) {
                double val;
                if(seriesList.size()>j) {
                    val=seriesList.get(j).getY().doubleValue();
                } else {
                    val=0d;
                }
                allValues.putScalar(j, i, val);
            }
        }
        allValues = allValues.transpose();
        INDArray softmax = softMax(allValues);
        for(int i = 0; i < cleanData.size(); i++) {
            Series<?> series = cleanData.get(i);
            List<Point> seriesList = ((PointSeries)series).getData();
            for(int j = 0; j < seriesList.size(); j++) {
                seriesList.get(j).setY(softmax.getDouble(i,j));
            }
        }
    }

    private static List<Series<?>> collectTechnologyData(Collection<String> portfolio, String seriesName, PortfolioList.Type inputType, int limit) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(seriesName);
        tagger.getTechnologiesFor(portfolio,inputType,limit).forEach(pair->{
            String tech = pair.getFirst();
            double prob = pair.getSecond();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });
        data.add(series);
        applySoftMax(data);
        return data;
    }

    public static List<Series<?>> collectTechnologyData(String portfolio, PortfolioList.Type inputType, int limit) {
        if (inputType.equals(PortfolioList.Type.patents)) {
            return collectTechnologyData(Arrays.asList(portfolio),portfolio,inputType,limit);
        } else {
            return collectTechnologyData(Database.possibleNamesForAssignee(portfolio),portfolio,inputType,limit);
        }
    }

    public static List<Series<?>> collectTechnologyData(Collection<String> patents, PortfolioList.Type inputType, int limit) {
        if (inputType.equals(PortfolioList.Type.patents)) {
            return collectTechnologyData(patents,"Patents",inputType,limit);
        } else {
            return collectTechnologyData(patents,"Companies",inputType,limit);
        }
    }

    public static List<Series<?>> collectLikelyAssetBuyersData(String portfolio, PortfolioList.Type inputType, int limit, Evaluator buyerModel, WeightLookupTable<VocabWord> lookupTable) {
        Collection<String> collection = inputType.equals(PortfolioList.Type.patents)?Arrays.asList(portfolio):Database.possibleNamesForAssignee(portfolio);
        return collectLikelyAssetBuyersData(collection,portfolio,inputType,limit,buyerModel,lookupTable);
    }

    public static List<Series<?>> collectLikelyAssetBuyersData(Collection<String> collection, String seriesName, PortfolioList.Type inputType, int limit, Evaluator buyerModel, WeightLookupTable<VocabWord> lookupTable) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(seriesName);
        ValueAttribute buyerAttr = new ValueAttribute("buyerValue",1.0,buyerModel);
        Evaluator similarityEvaluator = new SimilarityEvaluator(seriesName,lookupTable,new SimilarPatentFinder(collection,seriesName,lookupTable).computeAvg());
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(limit);
        Database.getAssignees().forEach(assignee->{
            if(inputType.equals(PortfolioList.Type.patents)||(!assignee.startsWith(seriesName)&&!seriesName.startsWith(assignee))) {
                double score = similarityEvaluator.evaluate(assignee)*buyerAttr.scoreAssignee(assignee);
                heap.add(new WordFrequencyPair<>(assignee,score));
            }
        });
        List<WordFrequencyPair<String,Double>> results = new ArrayList<>(limit);
        while(!heap.isEmpty()) {
            results.add(0,heap.remove());
        }
        results.forEach(e->{
            String tech = e.getFirst();
            double prob = e.getSecond();
            Point point = new Point(tech,prob);
            series.addPoint(point);
        });
        data.add(series);
        applySoftMax(data);
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
        Collection<String> patents = Database.selectPatentNumbersFromAssignee(company);
        return collectCompanyActivityData(patents,company);
    }


    public static List<Series<?>> collectCompanyActivityData(Collection<String> patents, String seriesName) {
        List<Series<?>> data = new ArrayList<>();
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
        series.setName(seriesName);
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
        Collection<String> collection = inputType.equals(PortfolioList.Type.assignees) ?
                Database.possibleNamesForAssignee(portfolio) : new HashSet<>(Arrays.asList(portfolio));
        return collectAverageValueData(collection,portfolio,evaluators);
    }

    public static List<Series<?>> collectAverageValueData(Collection<String> collection, String seriesName, List<Evaluator> evaluators) {
        // Weighted avg by portfolio size
        List<Series<?>> data = new ArrayList<>(1);
        if(collection.isEmpty()) return Collections.emptyList();
        PointSeries series = new PointSeries();
        series.setName(seriesName);
        evaluators.forEach(evaluator->{
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


    public static INDArray softMax(INDArray in) {
        INDArray shiftx = in.dup();
        INDArray max = shiftx.max(1);
        for(int i = 0; i < shiftx.rows(); i++) {
            shiftx.getRow(i).subi(max.getDouble(i));
        }
        INDArray exps = Transforms.exp(shiftx,false);
        INDArray sum =  exps.sum(1);
        for(int i = 0; i < shiftx.rows(); i++) {
            exps.getRow(i).divi(sum.getDouble(i));
        }
        return exps;
    }
}
