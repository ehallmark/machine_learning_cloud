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
import org.elasticsearch.index.query.InnerHitBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.join.query.HasParentQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
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
        final long Kw = 5000;
        final int k1 = 20;
        final int k2 = 5;
        final int k3 = 1;

        int year = 2010; // test year

        boolean runStage1 = true;
        boolean runStage2 = true;
        boolean runStage3 = true;
        boolean rebuildMMatrix = true;
        boolean runStage4 = true;
        boolean rebuildTMatrix = true;

        Stage1 stage1 = new Stage1(year);
        stage1.run(runStage1);

        Stage2 stage2 = new Stage2(stage1, Kw * k1);
        stage2.run(runStage2);

        Stage3 stage3 = new Stage3(stage2, Kw * k2, rebuildMMatrix, year);
        stage3.run(runStage3);

        Stage4 stage4 = new Stage4(stage3, Kw * k3, rebuildTMatrix, year);
        stage4.run(runStage4);

    }

    public static void reindex(Collection<MultiStem> multiStems) {
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(multiStem -> {
            multiStem.setIndex(cnt.getAndIncrement());
        });
    }

    public static Collection<MultiStem> applyFilters(KeywordScorer scorer, INDArray matrix, Collection<MultiStem> keywords, long targetNumToKeep, double minThreshold, double maxThreshold) {
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


    public static void streamElasticSearchData(int year, Function<SearchHit,Item> transformer) {
        LocalDate dateMin = LocalDate.of(year,1,1);
        LocalDate dateMax = dateMin.plusYears(1);
        TransportClient client = DataSearcher.getClient();
        SearchRequestBuilder search = client.prepareSearch(DataIngester.INDEX_NAME)
                .setTypes(DataIngester.TYPE_NAME)
                //.addSort(Constants.FILING_DATE, SortOrder.ASC)
                .setScroll(new TimeValue(60000))
                .setExplain(false)
                .setFrom(0)
                .setSize(10000)
                .setFetchSource(new String[]{Constants.ABSTRACT,Constants.INVENTION_TITLE},new String[]{})
                .setQuery(new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, QueryBuilders.boolQuery().filter(QueryBuilders.rangeQuery(Constants.FILING_DATE).gte(dateMin.toString()).lt(dateMax.toString())),true).innerHit(
                        new InnerHitBuilder().setSize(1).setFetchSourceContext(new FetchSourceContext(true, new String[]{Constants.FILING_DATE}, new String[]{}))
                ));
        if(debug) {
            System.out.println(search.request().toString());
        }

        SearchResponse response = search.get();
        DataSearcher.iterateOverSearchResults(response, transformer, -1, false);
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
