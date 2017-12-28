package models.keyphrase_prediction;

import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import models.keyphrase_prediction.models.DefaultModel;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.*;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;
import java.util.List;

/**
 * Created by ehallmark on 12/21/17.
 */
public class KeyphrasePredictionPipelineManager extends DefaultPipelineManager<WordCPCIterator,List<String>> {
    public static final Model modelParams = new DefaultModel();
    private WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    private static final File INPUT_DATA_FOLDER = new File("keyphrase_prediction_input_data/");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"keyphrase_prediction_model_predictions/predictions_map.jobj");
    public KeyphrasePredictionPipelineManager(WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
    }

    @Override
    public DataSetManager<WordCPCIterator> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }

    @Override
    public void rebuildPrerequisiteData() {
        initStages(false,true);
    }

    private void initStages(boolean rerunVocab, boolean rerunFilters) {
        // stage 1;
        Stage1 stage1 = new Stage1(modelParams);
        stage1.run(rerunVocab);
        //if(alwaysRerun)stage1.createVisualization();

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Stage2 stage2 = new Stage2(stage1.get(), modelParams);
        stage2.run(rerunFilters);
        //if(alwaysRerun)stage2.createVisualization();

        // valid word
        System.out.println("Pre-grouping data for valid word stage...");
        ValidWordStage validWordStage = new ValidWordStage(stage2.get(), modelParams);
        validWordStage.run(rerunFilters);

        // cpc density
        System.out.println("Pre-grouping data for cpc density...");
        CPCHierarchy hierarchy = new CPCHierarchy();
        CPCDensityStage cpcDensityStage = new CPCDensityStage(validWordStage.get(), modelParams, hierarchy);
        cpcDensityStage.run(rerunFilters);
        //if(alwaysRerun) stage4.createVisualization();

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Stage3 stage3 = new Stage3(cpcDensityStage.get(), modelParams);
        stage3.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();


    }

    @Override
    protected void initModel(boolean forceRecreateModel) {
        initStages(false,false);

    }

    @Override
    protected void splitData() {
    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
            datasetManager = wordCPC2VecPipelineManager.getDatasetManager();
        }
    }

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        final int maxSamples = 200;
        final int windowSize = 8;
        boolean rebuildPrerequisites = true;
        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 10;
        String modelName = modelParams.getModelName();

        String CPC2VecModelName = WordCPC2VecPipelineManager.MODEL_NAME;

        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(CPC2VecModelName,nEpochs,windowSize,maxSamples);
        KeyphrasePredictionPipelineManager pipelineManager = new KeyphrasePredictionPipelineManager(wordCPC2VecPipelineManager);

        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }
}
