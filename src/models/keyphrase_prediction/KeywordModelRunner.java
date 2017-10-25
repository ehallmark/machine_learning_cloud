package models.keyphrase_prediction;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.models.*;

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
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.SortBuilders;

import org.gephi.graph.api.Node;
import seeding.Constants;
import seeding.Database;

import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;
import visualization.Visualizer;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = false;

    public static void main(String[] args) {
        runModel();
    }

    public static void runModel() {
        Model model = new TimeDensityModel();

        boolean alwaysRerun = true;

        LocalDate now = LocalDate.now();
        int year;
        int maxYear = now.getYear();
        Map<String,List<String>> technologyMap = Collections.synchronizedMap(new HashMap<>());
        for(year=maxYear-25;year<=maxYear;year++) {

            // stage 1
            System.out.println("Starting year: "+year);

            Stage1 stage1 = new Stage1(model,year);
            stage1.run(alwaysRerun);
            //if(alwaysRerun)stage1.createVisualization();

            // time density stage
            System.out.println("Computing time densities...");

            Set<MultiStem> multiStems;

            // stage 2
            System.out.println("Pre-grouping data for stage 2...");
            Stage2 stage2 = new Stage2(stage1.get(), model, year);
            stage2.run(alwaysRerun);
            //if(alwaysRerun)stage2.createVisualization();
            multiStems = stage2.get();


            System.out.println("Pre-grouping data for time density stage...");
            TimeDensityStage timeDensityStage = new TimeDensityStage(multiStems, model, year);
            timeDensityStage.run(alwaysRerun);
            //if(alwaysRerun) timeDensityStage.createVisualization();
            multiStems = timeDensityStage.get();

            // stage 3
            System.out.println("Pre-grouping data for stage 3...");
            Stage3 stage3 = new Stage3(multiStems, model, year);
            stage3.run(alwaysRerun);
            //if(alwaysRerun) stage3.createVisualization();
            multiStems = stage3.get();

            // stage 4
            System.out.println("Pre-grouping data for cpc density stage...");
            System.out.println("Num multistems before CPC Density: "+multiStems.size());
            CPCDensityStage CPCDensityStage = new CPCDensityStage(multiStems, model, year);
            CPCDensityStage.run(alwaysRerun);
            // CPCDensityStage.createVisualization();
            multiStems = CPCDensityStage.get();
            System.out.println("Num multistems after CPC Density: "+multiStems.size());

            // stage 5
            System.out.println("Starting stage 5...");
            Stage5 stage5 = new Stage5(stage1, multiStems, model, year);
            stage5.run(alwaysRerun||year==maxYear);
            try {
                //stage5.createVisualization();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error on visualization...");
            }
            technologyMap.putAll(stage5.get());
            System.out.println("Num assets classified: " + stage5.get().size());
            System.out.println("Total num assets so far: " + technologyMap.size());
        }

        saveModelMap(model,technologyMap);

    }


    public static void updateLatest() {
        runModel();
    }


    public static Map<String,List<String>> loadModelMap(Model model) {
        return (Map<String,List<String>>) Database.tryLoadObject(new File(Constants.DATA_FOLDER+"keyword_asset_to_keyword_final_model_"+model.getModelName()+"_map.jobj"));
    }

    public static void saveModelMap(Model model, Map<String,List<String>> assetToTechnologyMap) {
        Database.trySaveObject(assetToTechnologyMap, new File(Constants.DATA_FOLDER+"keyword_asset_to_keyword_final_model_"+model.getModelName()+"_map.jobj"));
    }


    public static void reindex(Collection<MultiStem> multiStems) {
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(multiStem -> {
            multiStem.setIndex(cnt.getAndIncrement());
        });
    }

    public static void streamElasticSearchData(QueryBuilder query, Function<SearchHit,Item> transformer, int sampling) {
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

    public static void streamElasticSearchData(Function<SearchHit,Item> transformer, int year, int sampling) {
        BoolQueryBuilder query;
        if(sampling>0) {
            query = QueryBuilders.boolQuery()
                    .must(QueryBuilders.functionScoreQuery(QueryBuilders.matchAllQuery(),ScoreFunctionBuilders.randomFunction(23)))
                    .filter(QueryBuilders.rangeQuery(Constants.FILING_DATE)
                            .gte(""+(year-2)+"-01-01")
                            .lt(""+(year+3)+"-01-01")
                    );
        } else {
            query = QueryBuilders.boolQuery()
                    .filter(QueryBuilders.rangeQuery(Constants.FILING_DATE)
                            .gte(""+year+"-01-01")
                            .lt(""+(year+1)+"-01-01")
                    );
        }
        BoolQueryBuilder innerQuery = QueryBuilders.boolQuery().must(new HasParentQueryBuilder(DataIngester.PARENT_TYPE_NAME, query,false)
                .innerHit(new InnerHitBuilder()
                        .setSize(1)
                        .setFetchSourceContext(new FetchSourceContext(true, new String[]{Constants.FILING_DATE,Constants.WIPO_TECHNOLOGY}, new String[]{}))
                )).must(
                QueryBuilders.boolQuery()
                        .should(QueryBuilders.termQuery(Constants.GRANTED,false))
                        .should(QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.patents.toString()))
                        .minimumShouldMatch(1).filter(query)
        );

        if(sampling > 0) {
            // remove plant and design patents
            innerQuery = innerQuery
                    .mustNot(QueryBuilders.prefixQuery(Constants.NAME, "PP"))
                    .mustNot(QueryBuilders.prefixQuery(Constants.NAME, "D"));
        }

        query = QueryBuilders.boolQuery()
                .filter(innerQuery);

        streamElasticSearchData(query,transformer,sampling);
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
