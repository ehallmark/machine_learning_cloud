package models.wipo_technology_prediction;

import data_pipeline.models.ComputationGraphPredictionModel;
import seeding.Constants;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 12/13/17.
 */
public class WIPOPredictionModel extends ComputationGraphPredictionModel<String> {
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"wipo_prediction_model_data");

    private WIPOPredictionPipelineManager pipelineManager;
    public WIPOPredictionModel(WIPOPredictionPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, String> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    public void train(int nEpochs) {

    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }
}
