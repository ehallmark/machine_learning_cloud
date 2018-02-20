package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import data_pipeline.vectorize.PreSaveDataSetManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ehallmark on 11/7/17.
 */
public class CombinedCPC2Vec2VAEEncodingPipelineManager extends AbstractEncodingPipelineManager  {

    public static final String MODEL_NAME = "cpc2vec_2_vae_vec_encoding_model";
    public static final File PREDICTION_FILE = new File("cpc2vec_2_vae_vec_encoding_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("combined_deep_cpc_all3_vec_encoding_input_data");
    private static final int VECTOR_SIZE = 32;
    protected static final int BATCH_SIZE = 1024;
    protected static final int MINI_BATCH_SIZE = 1024;
    private static final int MAX_NETWORK_RECURSION = -1;
    private static int MAX_SAMPLE = 6;
    protected static final Random rand = new Random(235);
    private static CombinedCPC2Vec2VAEEncodingPipelineManager MANAGER;
    public CombinedCPC2Vec2VAEEncodingPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, DeepCPCVAEPipelineManager deepCPCVAEPipelineManager) {
        super(new File(currentDataFolderName(MAX_NETWORK_RECURSION,MAX_SAMPLE)),PREDICTION_FILE,modelName+MAX_SAMPLE,word2Vec,VECTOR_SIZE,BATCH_SIZE,MINI_BATCH_SIZE,MAX_SAMPLE,wordCPC2VecPipelineManager);
    }

    public static String currentDataFolderName(int recursion,int sample) {
        return (INPUT_DATA_FOLDER_ALL).getAbsolutePath()+sample+(recursion>0?("_r"+recursion):"");
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new CombinedCPC2Vec2VAEEncodingModel(this,modelName,VECTOR_SIZE);
        }
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
    protected MultiDataSetPreProcessor getTrainTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(MultiDataSet dataSet) {
                dataSet.setLabels(new INDArray[]{dataSet.getFeatures(1)});
                dataSet.setFeatures(new INDArray[]{dataSet.getFeatures(0)});
                dataSet.setLabelsMaskArray(null);
                dataSet.setFeaturesMaskArrays(null);
            }
        };
    }

    @Override
    public File getDevFile() {
        return FileTextDataSetIterator.devFile3;
    }

    public static synchronized CombinedCPC2Vec2VAEEncodingPipelineManager getOrLoadManager(boolean loadWord2Vec) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;


            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            if(loadWord2Vec) wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            DeepCPCVAEPipelineManager deepCPCVAEPipelineManager = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME);
            if(loadWord2Vec) deepCPCVAEPipelineManager.runPipeline(false,false,false,false,-1,false);

            setLoggingLevel(Level.INFO);
            MANAGER = new CombinedCPC2Vec2VAEEncodingPipelineManager(modelName, loadWord2Vec ? (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet() : null, wordCPC2VecPipelineManager, deepCPCVAEPipelineManager);
        }
        return MANAGER;
    }

    @Override
    protected void setDatasetManager() {
       throw new RuntimeException("Use combineddeepcpc2vecencodingmanager to setDatasetManager...");
    }

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        setCudaEnvironment();

        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        int nEpochs = 1;

        CombinedCPC2Vec2VAEEncodingPipelineManager pipelineManager = getOrLoadManager(rebuildDatasets);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}