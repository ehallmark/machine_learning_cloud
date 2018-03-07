package models.similarity_models.word_to_cpc.word_to_cpc_encoding_model;

import ch.qos.logback.classic.Level;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_to_cpc.AbstractWordToCPCPipelineManager;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;

/**
 * Created by ehallmark on 11/7/17.
 */
public class WordToCPCPipelineManager extends AbstractWordToCPCPipelineManager {
    public static final String MODEL_NAME = "word_to_cpc_encoder";
    public static final File VOCAB_MAP_FILE = new File(Constants.DATA_FOLDER+"word_to_cpc_encoding_word_idx_map.jobj");
    private static final int BATCH_SIZE = 128;
    private static final File INPUT_DATA_FOLDER = new File("word_to_cpc_encoding_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"word_to_cpc_encoding_predictions/predictions_map.jobj");

    public WordToCPCPipelineManager(String modelName, CPCVAEPipelineManager previousManager) {
        super(modelName,VOCAB_MAP_FILE,INPUT_DATA_FOLDER,PREDICTION_DATA_FILE,BATCH_SIZE,previousManager);
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

}
