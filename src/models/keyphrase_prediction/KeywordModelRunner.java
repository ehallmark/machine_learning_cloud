package models.keyphrase_prediction;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.scorers.KeywordScorer;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.scorers.TermhoodScorer;
import models.keyphrase_prediction.scorers.UnithoodScorer;
import models.keyphrase_prediction.stages.*;
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
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.Stemmer;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = false;
    public static void main(String[] args) {
        final long Kw = 1000;
        final int k1 = 20;
        final int k2 = 5;
        final int k3 = 1;

        final int windowSize = 4;

        boolean runStage1 = false;
        boolean runStage2 = false;
        boolean runStage3 = false;
        boolean rebuildMMatrix = false;
        boolean runStage4 = true;
        boolean rebuildTMatrix = true;


        Stage1 stage1 = new Stage1();
        stage1.run(runStage1);

        Stage2 stage2 = new Stage2(stage1, Kw * k1);
        stage2.run(runStage2);

        final int endYear = LocalDate.now().getYear();
        final int startYear = endYear - 20;

        // stage 3
        Map<Integer,Stage3> stage3Map = Collections.synchronizedMap(new HashMap<>());
        for(int i = startYear; i <= endYear; i++) {
            final int year = i;
            // group results by time windows in years
            Stage3 stage3 = new Stage3(stage2, Kw * k2, rebuildMMatrix, year);
            stage3.run(runStage3);

            stage3Map.put(year,stage3);
        }

        // stage 4
        System.out.println("Pre-grouping data for stage 4...");
        Map<Integer,Collection<MultiStem>> stage3TimeWindowStemMap = computeTimeWindowStemMap(startYear, endYear, windowSize, stage3Map);
        Map<Integer,Stage4> stage4Map = Collections.synchronizedMap(new HashMap<>());
        stage3TimeWindowStemMap.forEach((year,multiStems)->{
            Stage4 stage4 = new Stage4(multiStems, Kw * k3, rebuildTMatrix, year);
            stage4.run(runStage4);

            stage4Map.put(year,stage4);
        });

        Map<Integer,Collection<MultiStem>> stage4TimeWindowStemMap = computeTimeWindowStemMap(startYear, endYear, windowSize, stage4Map);

        Database.trySaveObject(stage4TimeWindowStemMap, new File("data/keyword_model_stage_4_time_window_stage_map.jobj"));
    }

    private static Map<Integer,Collection<MultiStem>> computeTimeWindowStemMap(int startYear, int endYear, int windowSize, Map<Integer,? extends Stage<Collection<MultiStem>>> stageMap) {
        Map<Integer,Collection<MultiStem>> timeWindowStemMap = Collections.synchronizedMap(new HashMap<>());
        for(int i = startYear; i < endYear; i++) {
            List<Collection<MultiStem>> multiStems = new ArrayList<>();
            for(int j = i; j <= i + windowSize; j++) {
                if(!stageMap.containsKey(j)) continue;
                multiStems.add(stageMap.get(j).get());
            }
            if(multiStems.isEmpty()) continue;
            Collection<MultiStem> mergedStems = multiStems.stream().flatMap(list->list.stream()).distinct().collect(Collectors.toList());
            timeWindowStemMap.put(i+windowSize,mergedStems);
            System.out.println("Num stems in "+(i+windowSize)+": "+mergedStems.size());
        }
        return timeWindowStemMap;
    }

    public static void reindex(Collection<MultiStem> multiStems) {
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(multiStem -> {
            multiStem.setIndex(cnt.getAndIncrement());
        });
    }

    public static Collection<MultiStem> applyFilters(KeywordScorer scorer, float[][] matrix, Collection<MultiStem> keywords, long targetNumToKeep, double minThreshold, double maxThreshold) {
        return scorer.scoreKeywords(keywords,matrix).entrySet().stream().filter(e->e.getValue()>=minThreshold&&e.getValue()<=maxThreshold).sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .limit(targetNumToKeep)
                .map(e->{
                    if(debug) {
                        System.out.println("Value for "+e.getKey().toString()+": "+e.getValue());
                    }
                    return e.getKey();
                })
                .collect(Collectors.toList());
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
            writer.write("Multi-Stem, Key Phrase\n");
            multiStems.forEach(e->{
                try {
                    writer.write(e.toString()+","+e.getBestPhrase()+"\n");
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
