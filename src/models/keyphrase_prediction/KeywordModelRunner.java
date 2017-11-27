package models.keyphrase_prediction;


import cpc_normalization.CPCHierarchy;

import models.keyphrase_prediction.models.*;

import models.keyphrase_prediction.stages.*;

import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word2vec_model.Word2VecModel;
import models.similarity_models.word2vec_model.Word2VecPipelineManager;
import models.similarity_models.word2vec_to_cpc_encoding_model.Word2VecToCPCPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;


import seeding.Constants;
import seeding.Database;


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

/**
 * Created by ehallmark on 9/11/17.
 */
public class KeywordModelRunner {
    public static final boolean debug = false;

    public static void main(String[] args) {
        runModel(false,false,false,true);
    }

    public static void runModel(boolean rerunVocab, boolean rerunFilters, boolean rerunWordEmbeddings, boolean rerunPredictions) {
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
        CPCDensityStage stage4 = new CPCDensityStage(stage3.get(), model, hierarchy);
        stage4.run(rerunFilters);
        //if(alwaysRerun) stage4.createVisualization();

        // stage 4
        System.out.println("Pre-grouping data for stage 5...");
        ValidWordStage stage5 = new ValidWordStage(stage4.get(), model);
        stage5.run(rerunFilters);
        //if(alwaysRerun) stage5.createVisualization();

        // Word2Vec model
        boolean rebuildDatasets = false;
        boolean runModels = rerunWordEmbeddings;
        boolean forceRecreateModels = false;
        boolean runPredictions = false; // NO PREDICTIONS FOR THIS MODEL
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;
        String modelName = Word2VecToCPCPipelineManager.MODEL_NAME;
        String cpcEncodingModel = CPCVAEPipelineManager.MODEL_NAME;
        String word2VecModelName = Word2VecPipelineManager.MODEL_NAME;

        // get latest keywords
        Map<String,String> stemToBestPhraseMap = stage5.get().stream().collect(Collectors.toMap(stem->stem.toString(),stem->stem.getBestPhrase()));
        Set<String> onlyWords = new HashSet<>(stemToBestPhraseMap.keySet());

        Word2VecModel word2VecModel = new Word2VecModel(new Word2VecPipelineManager(word2VecModelName,-1), word2VecModelName);
        try {
            word2VecModel.loadMostRecentModel();
        } catch(Exception e) {
            throw new RuntimeException("Unable to load word2vec...");
        }

        Word2VecToCPCPipelineManager pipelineManager = new Word2VecToCPCPipelineManager(modelName, onlyWords, stemToBestPhraseMap, (Word2Vec)word2VecModel.getNet(), new CPCVAEPipelineManager(cpcEncodingModel));
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);


        // K Means Stage
        KMeansStage kMeansStage = new KMeansStage(stage5.get(),stemToBestPhraseMap,stage1.get(),pipelineManager.getWord2Vec(),(MultiLayerNetwork)pipelineManager.getModel().getNet(), model);
        kMeansStage.run(runModels);

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
            multiStems.stream().sorted((m1,m2)->{
                float s1 = m1.getScore();
                float s2 = m2.getScore();
                return Float.compare(s2,s1);
            }).forEach(e->{
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
