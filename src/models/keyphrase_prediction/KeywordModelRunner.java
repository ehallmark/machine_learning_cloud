package models.keyphrase_prediction;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.scorers.KeywordScorer;

import models.keyphrase_prediction.stages.*;
import org.apache.commons.math3.distribution.AbstractRealDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.*;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;

import seeding.Constants;
import seeding.Database;

import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = false;
    public static void main(String[] args) {
        final long Kw = 5000;
        final int k1 = 20;
        final int k2 = 5;
        final int k3 = 1;

        final int minTokenFrequency = 30;
        final int maxTokenFrequency = 30000;

        final int windowSize = 4;
        final int maxCpcLength = 10;

        boolean runStage1 = true;
        boolean runStage2 = true;
        boolean runStage3 = true;
        boolean rebuildMMatrix = true;
        boolean runStage4 = true;
        boolean rebuildTMatrix = true;
        boolean runStage5 = true;


        final int endYear = LocalDate.now().getYear();
        final int startYear = endYear - 20;

        // stage 2
        Map<Integer,Stage1> stage1Map = Collections.synchronizedMap(new HashMap<>());
        for(int i = startYear; i <= endYear; i++) {
            final int year = i;
            // group results by time windows in years
            Stage1 stage1 = new Stage1(year,minTokenFrequency,maxTokenFrequency);
            stage1.run(runStage1);

            stage1Map.put(year,stage1);
        }

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Map<Integer,Map<MultiStem,AtomicLong>> stage1TimeWindowStemMap = computeTimeWindowCountMap(startYear, endYear, windowSize, stage1Map);
        Map<Integer,Stage2> stage2Map = Collections.synchronizedMap(new HashMap<>());
        stage1TimeWindowStemMap.forEach((year,countMap)->{
            Stage2 stage2 = new Stage2(countMap, year, Kw * k1);
            stage2.run(runStage2);

            stage2Map.put(year,stage2);
        });

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Map<Integer,Collection<MultiStem>> stage2TimeWindowStemMap = computeTimeWindowStemMap(startYear, endYear, windowSize, stage2Map);
        Map<Integer,Stage3> stage3Map = Collections.synchronizedMap(new HashMap<>());
        stage2TimeWindowStemMap.forEach((year,multiStems)->{
            Stage3 stage3 = new Stage3(multiStems, Kw * k2, rebuildMMatrix, year);
            stage3.run(runStage3);

            stage3Map.put(year,stage3);
        });

        // stage 4
        System.out.println("Pre-grouping data for stage 4...");
        Map<Integer,Collection<MultiStem>> stage3TimeWindowStemMap = computeTimeWindowStemMap(startYear, endYear, windowSize, stage3Map);
        Map<Integer,Stage4> stage4Map = Collections.synchronizedMap(new HashMap<>());
        stage3TimeWindowStemMap.forEach((year,multiStems)->{
            Stage4 stage4 = new Stage4(multiStems, Kw * k3, rebuildTMatrix, year, maxCpcLength);
            stage4.run(runStage4);

            stage4Map.put(year,stage4);
        });

        Map<Integer,Collection<MultiStem>> stage4TimeWindowStemMap = computeTimeWindowStemMap(startYear, endYear, windowSize, stage4Map);
        Database.trySaveObject(stage4TimeWindowStemMap, new File("data/keyword_model_stage_4_time_window_stage_map.jobj"));

        // stage 5
        /*// cpc statistics
        List<String> cpcCodes = Database.getClassCodes().stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct().collect(Collectors.toList());
        System.out.println("Num cpcs: "+cpcCodes.size());

        Map<String,Integer> cpcToIndexMap = IntStream.range(0,cpcCodes.size()).mapToObj(i->i).collect(Collectors.toMap(i->cpcCodes.get(i),i->i));

        /SparseRealMatrix matrix = new OpenMapRealMatrix(cpcCodes.size(),cpcCodes.size());
        AssetToCPCMap assetToCPCMap = new AssetToCPCMap();
        assetToCPCMap.getApplicationDataMap().entrySet().parallelStream().forEach(e->{
            Collection<String> cpcs = e.getValue().stream().map(cpc->cpc.length()>maxCpcLength?cpc.substring(0,maxCpcLength):cpc).distinct().collect(Collectors.toList());
            cpcs.forEach(cpc->{
                int idx1 = cpcToIndexMap.get(cpc);
                cpcs.forEach(cpc2->{
                    int idx2 = cpcToIndexMap.get(cpc2);
                    matrix.addToEntry(idx1,idx2,1);
                });
            });
        });*/

        System.out.println("Starting stage 5...");
        Map<Integer,Stage5> stage5Map = Collections.synchronizedMap(new HashMap<>());
        stage4TimeWindowStemMap.forEach((year,multiStems)->{
            Stage1 stage1 = stage1Map.get(year);
            if(stage1!=null) {
                Stage5 stage5 = new Stage5(stage1, multiStems, year);
                stage5.run(runStage5);

                stage5Map.put(year, stage5);
            }
        });

    }

    private static Map<Integer,Collection<MultiStem>> computeTimeWindowStemMap(int startYear, int endYear, int windowSize, Map<Integer,? extends Stage<Collection<MultiStem>>> stageMap) {
        Map<Integer,Collection<MultiStem>> timeWindowStemMap = Collections.synchronizedMap(new HashMap<>());
        for(int i = endYear; i >= startYear-windowSize; i--) {
            List<Collection<MultiStem>> multiStems = new ArrayList<>();
            for(int j = i; j <= i + windowSize; j++) {
                Stage<Collection<MultiStem>> stage = stageMap.get(j);
                if(stage!=null&&stage.get()!=null) {
                    multiStems.add(stage.get());
                }
            }
            if(multiStems.isEmpty()) continue;
            Collection<MultiStem> mergedStems = multiStems.stream().flatMap(list->list.stream()).distinct().collect(Collectors.toList());
            timeWindowStemMap.put(i+windowSize,mergedStems);
            System.out.println("Num stems in "+(i+windowSize)+": "+mergedStems.size());
        }
        return timeWindowStemMap;
    }

    private static Map<Integer,Map<MultiStem,AtomicLong>> computeTimeWindowCountMap(int startYear, int endYear, int windowSize, Map<Integer,? extends Stage<Map<MultiStem,AtomicLong>>> stageMap) {
        Map<Integer,Map<MultiStem,AtomicLong>> timeWindowStemMap = Collections.synchronizedMap(new HashMap<>());
        for(int i = endYear; i >= startYear-windowSize; i--) {
            List<Map<MultiStem,AtomicLong>> multiStems = new ArrayList<>();
            for(int j = i; j <= Math.min(i + windowSize, endYear); j++) {
                Stage<Map<MultiStem,AtomicLong>> stage = stageMap.get(j);
                if(stage!=null&&stage.get()!=null) {
                    multiStems.add(stage.get());
                }
            }
            if(multiStems.isEmpty()) continue;

            Map<MultiStem,AtomicLong> mergedCounts = multiStems.parallelStream().reduce((m1,m2)-> {
                Map<MultiStem, AtomicLong> m3 = Collections.synchronizedMap(new HashMap<>());
                m1.entrySet().forEach(e -> m3.put(e.getKey(), e.getValue()));
                m2.entrySet().forEach(e -> {
                    m3.putIfAbsent(e.getKey(), new AtomicLong(0));
                    m3.get(e.getKey()).getAndAdd(e.getValue().get());
                });
                return m3;
            }).get();

            timeWindowStemMap.put(i+windowSize,mergedCounts);
            System.out.println("Num stems in "+(i+windowSize)+": "+mergedCounts.size());
        }
        return timeWindowStemMap;
    }


    public static void reindex(Collection<MultiStem> multiStems) {
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(multiStem -> {
            multiStem.setIndex(cnt.getAndIncrement());
        });
    }

    public static Collection<MultiStem> applyFilters(KeywordScorer scorer, RealMatrix matrix, Collection<MultiStem> keywords, long maxNumToKeep, double lowerBoundPercent, double upperBoundPercent) {
        Map<MultiStem,Double> scoreMap = scorer.scoreKeywords(keywords,matrix);
        DoubleSummaryStatistics statistics = scoreMap.values().parallelStream().collect(Collectors.summarizingDouble(d->d));
        double mean = statistics.getAverage();
        long count = statistics.getCount();
        double sd = count < 2 ? 0 : Math.sqrt(scoreMap.values().parallelStream().mapToDouble(d->Math.pow(d-mean,2)).sum()/(count-1));
        if(sd==0) return Collections.emptyList();
        AbstractRealDistribution distribution = new NormalDistribution(mean,sd);
        return scoreMap.entrySet().stream()
                .filter(e->percentile(distribution,e.getValue())>=lowerBoundPercent&&percentile(distribution,e.getValue())<=upperBoundPercent)
                .sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .limit(maxNumToKeep)
                .map(e->{
                    if(debug) {
                        System.out.println("Value for "+e.getKey().toString()+": "+e.getValue());
                    }
                    e.getKey().setScore(e.getValue().floatValue());
                    return e.getKey();
                })
                .collect(Collectors.toList());
    }

    private static double percentile(AbstractRealDistribution distribution, double val) {
        return distribution.cumulativeProbability(val);
    }


    public static void streamElasticSearchData(int year, Function<SearchHit,Item> transformer, int sampling) {
        QueryBuilder query;
        if(year>0) {
            LocalDate dateMin = LocalDate.of(year, 1, 1);
            LocalDate dateMax = dateMin.plusYears(1);
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                    .filter(QueryBuilders.rangeQuery(Constants.FILING_DATE).gte(dateMin.toString()).lt(dateMax.toString()));
            if(sampling>0) {
                boolQuery = boolQuery.must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(),ScoreFunctionBuilders.randomFunction(23)));
            }
            query = new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, boolQuery,false)
                    .innerHit(new InnerHitBuilder()
                            .setSize(1)
                            .setFetchSourceContext(new FetchSourceContext(true, new String[]{Constants.FILING_DATE}, new String[]{}))
                    );
        } else {
            query = sampling>0 ? QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(), ScoreFunctionBuilders.randomFunction(69))
                    : QueryBuilders.matchAllQuery();
        }
        TransportClient client = DataSearcher.getClient();
        SearchRequestBuilder search = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                .setScroll(new TimeValue(60000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE},new String[]{})
                .setQuery(query);
        if(sampling>0) {
            search = search.addSort(SortBuilders.scoreSort());
        }
        if(debug) {
            System.out.println(search.request().toString());
        }

        SearchResponse response = search.get();
        DataSearcher.iterateOverSearchResults(response, transformer, sampling, false);
    }


    public static void writeToCSV(Collection<MultiStem> multiStems, File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Multi-Stem, Key Phrase, Score\n");
            multiStems.forEach(e->{
                try {
                    writer.write(e.toString()+","+e.getBestPhrase()+","+e.getScore()+"\n");
                }catch(Exception e2) {
                    e2.printStackTrace();
                }
            });
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
