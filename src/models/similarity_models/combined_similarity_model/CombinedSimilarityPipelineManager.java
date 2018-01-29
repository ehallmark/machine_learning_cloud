package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;

/**
 * Created by ehallmark on 11/7/17.
 */
public class CombinedSimilarityPipelineManager extends AbstractCombinedSimilarityPipelineManager {
    public static final String MODEL_NAME = "combined_similarity_model_small";
    public static final String MODEL_NAME_SMALL = "combined_similarity_model_compact16_small";
    public static final String MODEL_NAME_DEEP = "combined_similarity_model_deep16";
    private static final File INPUT_DATA_FOLDER = new File("combined_similarity_model_input_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"combined_similarity_model_predictions/predictions_map.jobj");

    public CombinedSimilarityPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, DefaultPipelineManager<DataSetIterator,INDArray> cpcvaePipelineManager) {
        super(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE,modelName,word2Vec,wordCPC2VecPipelineManager,cpcvaePipelineManager);

    }

    public void initModel(boolean forceRecreateModels) {
        CombinedSimilarityVAEPipelineManager vaePipelineManager = CombinedSimilarityVAEPipelineManager.getOrLoadManager();
        vaePipelineManager.runPipeline(false,false,false,false,-1,false,false);

        if(model==null) model = new CombinedSimilarityComputationGraph(this,modelName,(CombinedVariationalAutoencoder)vaePipelineManager.getModel());
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadMostRecentModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }


    @Override
    public File getDevFile() {
        return FileTextDataSetIterator.devFile3;
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        final boolean runDeepModel = CombinedSimilarityVAEPipelineManager.USE_DEEP_MODEL;

        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false; // NO PREDICTIONS FOR THIS MODEL
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;

        String modelName;
        String wordCpc2VecModel;
        DefaultPipelineManager<DataSetIterator,INDArray> cpcVaePipelineManager;
        if(runDeepModel) {
            modelName = MODEL_NAME_DEEP;
            cpcVaePipelineManager = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME);
            wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;
        } else {
            modelName = MODEL_NAME_SMALL;
            cpcVaePipelineManager = new CPCVAEPipelineManager(CPCVAEPipelineManager.MODEL_NAME);
            wordCpc2VecModel = WordCPC2VecPipelineManager.SMALL_MODEL_NAME;
        }

        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel,-1,-1,-1);
        wordCPC2VecPipelineManager.runPipeline(false,false,false,false,-1,false);

        setLoggingLevel(Level.INFO);
        CombinedSimilarityPipelineManager pipelineManager = new CombinedSimilarityPipelineManager(modelName, (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet(), wordCPC2VecPipelineManager, cpcVaePipelineManager);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
