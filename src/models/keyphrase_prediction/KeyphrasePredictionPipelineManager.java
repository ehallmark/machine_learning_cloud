package models.keyphrase_prediction;

import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import models.keyphrase_prediction.models.DefaultModel;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.*;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word2vec_model.Word2VecModel;
import models.similarity_models.word2vec_model.Word2VecPipelineManager;
import models.similarity_models.word2vec_to_cpc_encoding_model.Word2VecToCPCPipelineManager;
import models.text_streaming.ESTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/21/17.
 */
public class KeyphrasePredictionPipelineManager extends DefaultPipelineManager<SequenceIterator<VocabWord>,List<String>> {
    private static final Model modelParams = new DefaultModel();
    public KeyphrasePredictionPipelineManager(File dataFolder, File finalPredictionsFile) {
        super(dataFolder, finalPredictionsFile);
    }

    @Override
    public DataSetManager<SequenceIterator<VocabWord>> getDatasetManager() {
        return null;
    }

    @Override
    public void rebuildPrerequisiteData() {
        try {
            System.out.println("Starting to pull latest text data from elasticsearch...");
            ESTextDataSetIterator.main(null);
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        boolean rerunVocab = false;
        boolean rerunFilters = false;

        // stage 1;
        Stage1 stage1 = new Stage1(modelParams);
        stage1.run(rerunVocab);
        //if(alwaysRerun)stage1.createVisualization();

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Stage2 stage2 = new Stage2(stage1.get(), modelParams);
        stage2.run(rerunFilters);
        //if(alwaysRerun)stage2.createVisualization();

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Stage3 stage3 = new Stage3(stage2.get(), modelParams);
        stage3.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        // stage 4
        System.out.println("Pre-grouping data for stage 4...");
        CPCHierarchy hierarchy = new CPCHierarchy();
        CPCDensityStage stage4 = new CPCDensityStage(stage3.get(), modelParams, hierarchy);
        stage4.run(rerunFilters);
        //if(alwaysRerun) stage4.createVisualization();

        // stage 4
        System.out.println("Pre-grouping data for stage 5...");
        ValidWordStage stage5 = new ValidWordStage(stage4.get(), modelParams);
        stage5.run(rerunFilters);
    }

    @Override
    protected void initModel(boolean forceRecreateModel) {
        int nEpochs = 5;
        String modelName = Word2VecToCPCPipelineManager.MODEL_NAME;
        String cpcEncodingModel = CPCVAEPipelineManager.MODEL_NAME;
        String word2VecModelName = Word2VecPipelineManager.MODEL_NAME;

        // get latest keywords
        ValidWordStage stage5 = new ValidWordStage(null,modelParams);
        stage5.run(false);
        Map<String,String> stemToBestPhraseMap = stage5.get().stream().collect(Collectors.toMap(stem->stem.toString(), stem->stem.getBestPhrase()));
        Set<String> onlyWords = new HashSet<>(stemToBestPhraseMap.keySet());

        Word2VecModel word2VecModel = new models.similarity_models.word2vec_model.Word2VecModel(new Word2VecPipelineManager(word2VecModelName,-1), word2VecModelName);
        try {
            word2VecModel.loadMostRecentModel();
        } catch(Exception e) {
            throw new RuntimeException("Unable to load word2vec...");
        }
    }

    @Override
    protected void splitData() {
        Set<String> allFilings = new HashSet<>();
        allFilings.addAll(new FilingToAssetMap().getPatentDataMap().keySet());
        allFilings.addAll(new FilingToAssetMap().getApplicationDataMap().keySet());

        List<String> allData = Collections.synchronizedList(allFilings.stream().sorted().collect(Collectors.toList()));

        Collections.shuffle(allData, new Random(23));

        trainAssets = allData;
        testAssets = Collections.emptyList();
        validationAssets = Collections.emptyList();
    }

    @Override
    protected void setDatasetManager() {

    }
}
