package models.similarity_models.cpc2vec_model;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import lombok.Getter;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ehallmark on 11/21/17.
 */
public class CPC2VecPipelineManager extends DefaultPipelineManager<SequenceIterator<VocabWord>,INDArray> {
    public static final String MODEL_NAME = "cpc2vec_model";
    private static final File INPUT_DATA_FOLDER = new File("cpc2vec_input_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"cpc2vec_predictions/predictions_map.jobj");

    private String modelName;
    @Getter
    private int numEpochs;
    @Getter
    private List<String> testWords;
    public CPC2VecPipelineManager(String modelName, int numEpochs) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.numEpochs=numEpochs;
        this.modelName=modelName;
        this.testWords = Arrays.asList("A","B","C","D","E","F","G","A02","BO3Q","Y","C07F","A02A1/00");
    }

    @Override
    public void rebuildPrerequisiteData() {

    }

    protected void initModel(boolean forceRecreateModels) {
        model = new CPC2VecModel(this, modelName);
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
    protected void splitData() {
        // purposefully do nothing
    }


    @Override
    public DataSetManager<SequenceIterator<VocabWord>> getDatasetManager() {
        return null;
    }

    @Override
    protected void setDatasetManager() {

    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 10;
        String modelName = MODEL_NAME;

        CPC2VecPipelineManager pipelineManager = new CPC2VecPipelineManager(modelName,nEpochs);

        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }


}
