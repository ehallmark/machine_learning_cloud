package highcharts;

import server.SimilarPatentServer;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import similarity_models.paragraph_vectors.WordFrequencyPair;
import genetics.lead_development.*;
import com.google.common.util.concurrent.AtomicDouble;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.Series;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import tools.MinHeap;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.value.ValueAttr;
import ui_models.attributes.classification.TechTaggerNormalizer;
import ui_models.attributes.value.SimilarityEvaluator;
import ui_models.portfolios.PortfolioList;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 2/14/17.
 */
public class HighchartDataAdapter {
    private static final ClassificationAttr tagger;
    private static final int NUM_MILLISECONDS_IN_A_DAY = 86400000;
    static {
        tagger = TechTaggerNormalizer.getDefaultTechTagger();
    }

    public static List<Series<?>> collectValueTimelineData(String assignee, ValueAttr valueModel) {
        return collectValueTimelineData(Database.selectPatentNumbersFromAssignee(assignee),valueModel);
    }
    public static List<Series<?>> collectValueTimelineData(Collection<String> patents, ValueAttr valueModel) {
        List<Series<?>> data = new ArrayList<>();
        int numMonths = 6;
        Map<LocalDate,Set<String>> dateToPatentMap = new HashMap<>();
        patents.forEach(patent->{
            LocalDate date = Database.getPubDateFor(patent);
            if(date!=null) {
                // groups by semester
                if(date.getMonthValue()>6) {
                    date = date.withMonth(7).withDayOfMonth(1);
                } else {
                    date = date.withMonth(1).withDayOfMonth(1);
                }
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
                date = date.plusMonths(numMonths);
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
        if(true) throw new UnsupportedOperationException("Not yet implemented");
        List<Series<?>> data = new ArrayList<>();
        int numMonths = 6;
        Map<LocalDate,Set<String>> dateToPatentMap = new HashMap<>();
        patents.forEach(patent->{
            LocalDate date = Database.getPubDateFor(patent);
            if(date!=null) {
                // groups by semester
                if(date.getMonthValue()>6) {
                    date = date.withMonth(7).withDayOfMonth(1);
                } else {
                    date = date.withMonth(1).withDayOfMonth(1);
                }
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
                date = date.plusMonths(numMonths);
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
        tagger.attributesFor(patentSample,100).forEach(pair->{
            String tech = pair.getFirst();
            PointSeries series = new PointSeries();
            series.setName(tech);
            dates.forEach(date->{
                List<String> set = new ArrayList<>(dateToPatentMap.get(date));
                Collections.shuffle(set);
                Point point = new Point().setX(date.toEpochDay()*NUM_MILLISECONDS_IN_A_DAY);
                if(set!=null) {
                    // TODO
                    //point.setY(tagger.getTechnologyValueFor(PortfolioList.abstractPorfolioList(set.stream().limit(100).collect(Collectors.toList()), tech, PortfolioList.Type.patents));
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
        tagger.attributesFor(portfolio,limit).forEach(pair->{
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

    public static List<Series<?>> collectLikelyAssetBuyersData(String portfolio, PortfolioList.Type inputType, int limit, ValueAttr buyerModel, Map<String,INDArray> lookupTable) {
        Collection<String> collection = inputType.equals(PortfolioList.Type.patents)?Arrays.asList(portfolio):Database.possibleNamesForAssignee(portfolio);
        return collectLikelyAssetBuyersData(collection,portfolio,inputType,limit,buyerModel,lookupTable);
    }

    public static List<Series<?>> collectLikelyAssetBuyersData(Collection<String> collection, String seriesName, PortfolioList.Type inputType, int limit, ValueAttr buyerModel, Map<String,INDArray> lookupTable) {
        List<Series<?>> data = new ArrayList<>();
        PointSeries series = new PointSeries();
        series.setName(seriesName);
        ValueAttribute buyerAttr = new ValueAttribute("buyerValue",1.0,buyerModel);
        ValueAttr similarityEvaluator = new SimilarityEvaluator(seriesName,lookupTable,new SimilarPatentFinder(collection,seriesName).computeAvg());
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
        portfolioList.getItemList().forEach(p->{
            Point point = new Point(p.getName(),similarity?(p.getSimilarity()*100):p.getValue()); // for visualizing percentages
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

    public static List<Series<?>> collectAverageValueData(String portfolio, PortfolioList.Type inputType, List<ValueAttr> evaluators) {
        Collection<String> collection = inputType.equals(PortfolioList.Type.assignees) ?
                Database.possibleNamesForAssignee(portfolio) : new HashSet<>(Arrays.asList(portfolio));
        return collectAverageValueData(collection,portfolio,evaluators);
    }

    public static List<Series<?>> collectAverageValueData(Collection<String> collection, String seriesName, List<ValueAttr> evaluators) {
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
            Point point = new Point(SimilarPatentServer.humanAttributeFor(evaluator.getName()),value.get());
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
