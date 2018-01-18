package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public class CombinedSimilarityVAEPipelineManager extends AbstractCombinedSimilarityPipelineManager {
    public static final String MODEL_NAME = "combined_similarity_vae";
    public static final String MODEL_NAME_SMALL = "combined_compact16_similarity_vae";
    public static final Map<String,Integer> NAME_TO_VECTOR_SIZE_MAP = Collections.synchronizedMap(new HashMap<>());
    public static final Map<String,File> NAME_TO_PREDICTION_FILE_MAP = Collections.synchronizedMap(new HashMap<>());
    static {
        NAME_TO_VECTOR_SIZE_MAP.put(MODEL_NAME,32);
        NAME_TO_VECTOR_SIZE_MAP.put(MODEL_NAME_SMALL,16);
        NAME_TO_PREDICTION_FILE_MAP.put(MODEL_NAME,new File(Constants.DATA_FOLDER+"combined_similarity_vae_predictions/predictions_map.jobj"));
        NAME_TO_PREDICTION_FILE_MAP.put(MODEL_NAME_SMALL,new File(Constants.DATA_FOLDER+"combined_similarity_small_vae_predictions/predictions_map.jobj"));

    }
    private static final File INPUT_DATA_FOLDER = new File("combined_similarity_model_input_data");
    private static CombinedSimilarityVAEPipelineManager MANAGER;
    public CombinedSimilarityVAEPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, CPCVAEPipelineManager cpcvaePipelineManager) {
        super(INPUT_DATA_FOLDER,NAME_TO_PREDICTION_FILE_MAP.get(modelName),modelName,word2Vec,wordCPC2VecPipelineManager,cpcvaePipelineManager);
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new CombinedVariationalAutoencoder(this,modelName,NAME_TO_VECTOR_SIZE_MAP.get(modelName));
        }
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadBestModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    public static synchronized CombinedSimilarityVAEPipelineManager getOrLoadManager() {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME_SMALL;
            String cpcEncodingModel = CPCVAEPipelineManager.MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.SMALL_MODEL_NAME;

            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            setLoggingLevel(Level.INFO);
            MANAGER = new CombinedSimilarityVAEPipelineManager(modelName, (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet(), wordCPC2VecPipelineManager, new CPCVAEPipelineManager(cpcEncodingModel));
        }
        return MANAGER;
    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        int nEpochs = 5;

        CombinedSimilarityVAEPipelineManager pipelineManager = getOrLoadManager();
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
