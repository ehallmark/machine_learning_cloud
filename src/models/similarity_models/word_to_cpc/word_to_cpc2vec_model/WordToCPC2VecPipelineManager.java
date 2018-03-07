package models.similarity_models.word_to_cpc.word_to_cpc2vec_model;

import ch.qos.logback.classic.Level;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import models.similarity_models.cpc2vec_model.CPC2VecPipelineManager;
import models.similarity_models.word_to_cpc.AbstractWordToCPCPipelineManager;
import models.text_streaming.FileTextDataSetIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;

/**
 * Created by ehallmark on 11/7/17.
 */
public class WordToCPC2VecPipelineManager extends AbstractWordToCPCPipelineManager {
    public static final String MODEL_NAME = "word_to_cpc2vec_encoder";
    public static final File VOCAB_MAP_FILE = new File(Constants.DATA_FOLDER+"word_to_cpc2vec_word_idx_map.jobj");
    private static final int BATCH_SIZE = 128;
    private static final File INPUT_DATA_FOLDER = new File("word_to_cpc2vec_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"word_to_cpc2vec_predictions/predictions_map.jobj");

    public WordToCPC2VecPipelineManager(String modelName, CPC2VecPipelineManager previousManager) {
        super(modelName,VOCAB_MAP_FILE,INPUT_DATA_FOLDER,PREDICTION_DATA_FILE,BATCH_SIZE,previousManager);
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new WordToCPC2VecModel(this, modelName);
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
    public synchronized DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }


    @Override
    protected void setDatasetManager() {
        datasetManager = new NoSaveDataSetManager<>(
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TRAIN)),
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.DEV1)),
                getRawIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TEST))
        );
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false; // NO PREDICTIONS FOR THIS MODEL
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;
        String modelName = MODEL_NAME;
        String cpcEncodingModel = CPC2VecPipelineManager.MODEL_NAME;

        setLoggingLevel(Level.INFO);

        WordToCPC2VecPipelineManager pipelineManager = new WordToCPC2VecPipelineManager(modelName, new CPC2VecPipelineManager(cpcEncodingModel,-1));

        rebuildPrerequisites = rebuildPrerequisites || pipelineManager.loadVocabMap()==null; // Check if vocab map exists

        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
