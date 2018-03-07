package models.similarity_models.word_cpc_2_vec_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import lombok.Getter;
import lombok.Setter;
import models.text_streaming.FileTextDataSetIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 11/21/17.
 */
public class ParagraphCPCVecPipelineManager extends AbstractWordCPC2VecPipelineManager<ParagraphCPCIterator> {
    public static final String MODEL_NAME = "paragraph_cpc_2_vec_model";
    private static final int VECTOR_SIZE = 256;
    private static final File INPUT_DATA_FOLDER = new File("paragraph_cpc_2_vec_input_data");
    private static final File PREDICTIONS_FILE = new File(Constants.DATA_FOLDER+"paragraph_cpc_2_vec_predictions/predictions_map.jobj");
    protected Map<String,Collection<CPC>> cpcMap;
    private String modelName;
    public ParagraphCPCVecPipelineManager(String modelName, int numEpochs, int maxSamples) {
        super(INPUT_DATA_FOLDER, PREDICTIONS_FILE,numEpochs,maxSamples);
        this.numEpochs=numEpochs;
        this.maxSamples=maxSamples;
        this.modelName=modelName;
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
        model = new ParagraphCPC2VecModel(this, modelName, VECTOR_SIZE);
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
    protected ParagraphCPCIterator getSequenceIterator(FileTextDataSetIterator iterator, int nEpochs) {
        File baseDir = FileTextDataSetIterator.BASE_DIR;
        boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
        return new ParagraphCPCIterator(iterator,nEpochs,getCPCMap(), maxSamples,fullText);
    }

    private static ParagraphCPCVecPipelineManager MANAGER = null;
    public static ParagraphCPCVecPipelineManager getOrLoadManager(String modelName, boolean loadWord2Vec, int nEpochs) {
        if(MANAGER==null) {

            final int maxSamples;

            maxSamples = 500;
            MANAGER = new ParagraphCPCVecPipelineManager(modelName,nEpochs,maxSamples);
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

        ParagraphCPCVecPipelineManager pipelineManager = getOrLoadManager(MODEL_NAME,false, nEpochs);
        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }


}
