package models.keyphrase_prediction;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.keyphrase_prediction.models.*;

import models.keyphrase_prediction.stages.*;
import models.text_streaming.FileTextDataSetIterator;
import org.apache.commons.io.FileUtils;
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
        runModel(false,false,true);
    }

    public static void runModel(boolean rerunVocab, boolean rerunFilters, boolean rerunPredictions) {
        Model model = new TimeDensityModel();

        // stage 1;
        Stage1 stage1 = new Stage1(model);
        stage1.run(rerunVocab);
        //if(alwaysRerun)stage1.createVisualization();

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Stage2 stage2 = new Stage2(stage1.get(), model);
        stage2.run(rerunFilters);
        //if(alwaysRerun)stage2.createVisualization();

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Stage3 stage3 = new Stage3(stage2.get(), model);
        stage3.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        // stage 4
        System.out.println("Pre-grouping data for stage 4...");
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();
        CPCDensityStage stage4 = new CPCDensityStage(stage3.get(), model, hierarchy);
        stage4.run(rerunFilters);
        //if(alwaysRerun) stage4.createVisualization();

        // stage 4
        System.out.println("Pre-grouping data for stage 5...");
        ValidWordStage stage5 = new ValidWordStage(stage4.get(), model);
        stage5.run(rerunFilters);
        //if(alwaysRerun) stage5.createVisualization();

        if(rerunPredictions) {
            // TODO technology predictions
            //Map<String,List<String>> technologyMap = Collections.synchronizedMap(new HashMap<>());
            //saveModelMap(model,technologyMap);
        }

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
