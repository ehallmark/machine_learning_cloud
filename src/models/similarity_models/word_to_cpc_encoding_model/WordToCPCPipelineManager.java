package models.similarity_models.word_to_cpc_encoding_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DatasetManager;
import lombok.Getter;
import lombok.Setter;
import models.similarity_models.cpc_encoding_model.CPCIndexMap;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import models.similarity_models.signatures.CPCDataSetIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.File;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/7/17.
 */
public class WordToCPCPipelineManager extends DefaultPipelineManager<INDArray> {
    public static final String MODEL_NAME = "word_to_cpc_encoder";
    public static final File currentVocabMapFile = new File(Constants.DATA_FOLDER+"word_to_cpc_encoding_word_idx_map.jobj");
    private static final int BATCH_SIZE = 128;
    private static final File INPUT_DATA_FOLDER = new File("word_to_cpc_encoding_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"word_to_cpc_encoding_predictions/predictions_map.jobj");
    @Getter
    private Map<String,Integer> wordToIdxMap;
    private String modelName;
    private Map<String,INDArray> assetToEncodingMap;
    public WordToCPCPipelineManager(String modelName, CPCVAEPipelineManager previousManager) {
        super(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE);
        this.modelName=modelName;
        assetToEncodingMap = previousManager.loadPredictions();
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new WordToCPCEncodingNN(this, modelName);
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadBestModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    @Override
    public void rebuildPrerequisiteData() {
        // update vocabulary
        System.out.println("Rebuilding vocabulary map...");
        final int vocabSampling = 2000000;
        final int minDocCount = 10;
        final int maxDocCount = Math.round(0.2f*vocabSampling);
        WordToCPCIterator vocabIter = new WordToCPCIterator(vocabSampling,BATCH_SIZE);
        vocabIter.buildVocabMap(minDocCount,maxDocCount);
        wordToIdxMap = vocabIter.getWordToIdxMap();
        System.out.println("Vocab size: "+wordToIdxMap.size());
        saveVocabMap();
    }

    private void saveVocabMap() {
        Database.trySaveObject(wordToIdxMap,currentVocabMapFile);
    }

    private Map<String,Integer> loadVocabMap() {
        wordToIdxMap = (Map<String,Integer>)Database.tryLoadObject(currentVocabMapFile);
        return wordToIdxMap;
    }

    @Override
    public synchronized DatasetManager getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new DatasetManager(dataFolder);
        }
        return datasetManager;
    }

    public int getBatchSize() {
        return BATCH_SIZE;
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        // handled by Elasticsearch
    }

    @Override
    protected void setDatasetManager() {
        int limit = 5000000;
        int numTests = 50000;
        double testRatio = new Double(numTests)/limit;
        datasetManager = new DatasetManager(dataFolder,
                getRawIterator(limit),
                testRatio,
                testRatio
        );
    }


    protected DataSetIterator getRawIterator(int limit) {
        return new WordToCPCIterator(limit,assetToEncodingMap,getWordToIdxMap(),BATCH_SIZE,false,false,false);
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false; // NO PREDICTIONS FOR THIS MODEL
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;
        String modelName = MODEL_NAME;
        String cpcEncodingModel = CPCVAEPipelineManager.MODEL_NAME;

        setLoggingLevel(Level.INFO);
        WordToCPCPipelineManager pipelineManager = new WordToCPCPipelineManager(modelName, new CPCVAEPipelineManager(cpcEncodingModel));

        rebuildPrerequisites = rebuildPrerequisites || pipelineManager.loadVocabMap()==null; // Check if vocab map exists

        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

    public static void setLoggingLevel(Level level) {
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.setLevel(level);
        } catch (Exception e) {
            System.out.println("Error setting log level: "+e.getMessage());
        }
    }
}
