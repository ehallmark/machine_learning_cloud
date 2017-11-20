package models.similarity_models.deep_word_to_cpc_encoding_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_to_cpc_encoding_model.WordToCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public class DeepWordToCPCPipelineManager extends DefaultPipelineManager<INDArray> {
    public static final String MODEL_NAME = "deep_word_to_cpc_encoder";
    public static final File currentVocabMapFile = new File(Constants.DATA_FOLDER+"deep_word_to_cpc_encoding_word_idx_map.jobj");
    private static final int BATCH_SIZE = 128;
    private static final File INPUT_DATA_FOLDER = new File("deep_word_to_cpc_encoding_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"deep_word_to_cpc_encoding_predictions/predictions_map.jobj");
    @Getter
    private Map<String,Integer> wordToIdxMap;
    private String modelName;
    private Map<String,INDArray> assetToEncodingMap;
    private CPCVAEPipelineManager previousManager;
    public DeepWordToCPCPipelineManager(String modelName, CPCVAEPipelineManager previousManager) {
        super(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE);
        this.modelName=modelName;
        this.previousManager=previousManager;
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new DeepWordToCPCEncodingNN(this, modelName);
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
        final int totalDocCount = 5000000;
        LabelAwareIterator iterator = new FileTextDataSetIterator(FileTextDataSetIterator.Type.TRAIN);
        final int minDocCount = 10;
        final int maxDocCount = Math.round(0.3f*totalDocCount);
        WordToCPCIterator vocabIter = new WordToCPCIterator(iterator,BATCH_SIZE);
        vocabIter.buildVocabMap(minDocCount,maxDocCount);
        wordToIdxMap = vocabIter.getWordToIdxMap();
        System.out.println("Vocab size: "+wordToIdxMap.size());
        saveVocabMap();
    }

    private void saveVocabMap() {
        Database.trySaveObject(wordToIdxMap,currentVocabMapFile);
    }

    public Map<String,Integer> loadVocabMap() {
        wordToIdxMap = (Map<String,Integer>)Database.tryLoadObject(currentVocabMapFile);
        return wordToIdxMap;
    }

    @Override
    public synchronized DataSetManager getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new PreSaveDataSetManager(dataFolder);
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
        datasetManager = new PreSaveDataSetManager(dataFolder,
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TRAIN)),
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.DEV1)),
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TEST))
        );
    }

    protected Map<String,INDArray> getAssetToEncodingMap() {
        if(assetToEncodingMap==null) {
            assetToEncodingMap = previousManager.loadPredictions();
        }
        return assetToEncodingMap;
    }


    protected DataSetIterator getRawIterator(LabelAwareIterator iterator) {
        return new WordToCPCIterator(iterator,getAssetToEncodingMap(),getWordToIdxMap(),BATCH_SIZE,false,false,false);
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
        DeepWordToCPCPipelineManager pipelineManager = new DeepWordToCPCPipelineManager(modelName, new CPCVAEPipelineManager(cpcEncodingModel));

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
