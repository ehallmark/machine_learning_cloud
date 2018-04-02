package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import lombok.Setter;
import models.keyphrase_prediction.KeyphrasePredictionPipelineManager;
import models.keyphrase_prediction.stages.Stage1;
import models.text_streaming.FileTextDataSetIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public class WordCPC2VecPipelineManager extends AbstractWordCPC2VecPipelineManager<WordCPCIterator> {
    public static final String SMALL_MODEL_NAME = "32smallwordcpc2vec_model";
    public static final String DEEP_MODEL_NAME = "wordcpc2vec_model_deep";
    public static final String DEEP256_MODEL_NAME = "wordcpc2vec256_model_deep";
    private static final int SMALL_VECTOR_SIZE = 32;
    private static final int LARGE_VECTOR_SIZE = 32;
    private static final int DEEP256_VECTOR_SIZE = 256;
    public static final Map<String,Integer> modelNameToVectorSizeMap = Collections.synchronizedMap(new HashMap<>());
    public static final Map<String,File> modelNameToPredictionFileMap = Collections.synchronizedMap(new HashMap<>());
    static {
        modelNameToVectorSizeMap.put(SMALL_MODEL_NAME,SMALL_VECTOR_SIZE);
        modelNameToVectorSizeMap.put(DEEP256_MODEL_NAME,DEEP256_VECTOR_SIZE);
        modelNameToVectorSizeMap.put(DEEP_MODEL_NAME,LARGE_VECTOR_SIZE);
        modelNameToPredictionFileMap.put(SMALL_MODEL_NAME,new File(Constants.DATA_FOLDER+"wordcpc2vec_predictions/predictions_map.jobj"));
        modelNameToPredictionFileMap.put(DEEP_MODEL_NAME,new File(Constants.DATA_FOLDER+"wordcpc2vec_deep_predictions/predictions_map.jobj"));
        modelNameToPredictionFileMap.put(DEEP256_MODEL_NAME,new File(Constants.DATA_FOLDER+"wordcpc2vec256_deep_predictions/predictions_map.jobj"));

    }
    protected String modelName;
    private static final File INPUT_DATA_FOLDER = new File("wordcpc2vec_input_data");
    @Getter
    protected int windowSize;
    public WordCPC2VecPipelineManager(String modelName, int numEpochs, int windowSize, int maxSamples) {
        super(INPUT_DATA_FOLDER, modelNameToPredictionFileMap.get(modelName),numEpochs,maxSamples);
        this.numEpochs=numEpochs;
        this.maxSamples=maxSamples;
        this.modelName=modelName;
        this.windowSize=windowSize;
        this.testWords = Arrays.asList("A","B","C","D","E","F","G","A02","BO3Q","Y","C07F","A02A1/00","semiconductor","computer","internet","virtual","intelligence","artificial","chemistry","biology","electricity","agriculture","automobile","robot");
    }

    public Map<String,INDArray> getOrLoadWordVectors() {
        Map<String,Map<String,INDArray>> predictions = loadPredictions();
        if(predictions==null) return null;
        return predictions.get(WordCPC2VecModel.WORD_VECTORS);
    }

    public Map<String,INDArray> getOrLoadCPCVectors() {
        Map<String,Map<String,INDArray>> predictions = loadPredictions();
        if(predictions==null) return null;
        return predictions.get(WordCPC2VecModel.CLASS_VECTORS);
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new WordCPC2VecModel(this, modelName, modelNameToVectorSizeMap.get(modelName));
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
    protected WordCPCIterator getSequenceIterator(FileTextDataSetIterator iterator, int nEpochs) {
        File baseDir = FileTextDataSetIterator.BASE_DIR;
        boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
        return new WordCPCIterator(iterator,nEpochs, maxSamples,fullText);
    }

    private static WordCPC2VecPipelineManager MANAGER = null;
    public static WordCPC2VecPipelineManager getOrLoadManager(String modelName, boolean loadWord2Vec, int nEpochs) {
        if(MANAGER==null) {

            final int maxSamples;
            final int windowSize;

            windowSize = 6;//4;
            maxSamples = 500;
            MANAGER = new WordCPC2VecPipelineManager(modelName,nEpochs,windowSize,maxSamples);
            if(loadWord2Vec) {
                MANAGER.runPipeline(false,false,false,false,-1,false);
            }

        }
        return MANAGER;
    }


    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        setCudaEnvironment();


        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = true;
        int nEpochs = 5;

        WordCPC2VecPipelineManager pipelineManager = getOrLoadManager(DEEP256_MODEL_NAME,false, nEpochs);
        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }


}
