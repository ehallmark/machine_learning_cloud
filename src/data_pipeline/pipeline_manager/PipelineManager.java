package data_pipeline.pipeline_manager;

import data_pipeline.vectorize.DataSetManager;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public interface PipelineManager<D,T> {
    DataSetManager<D> getDatasetManager();
    void rebuildPrerequisiteData();
    void saveRawDatasets();
    void trainModels(int nEpochs);
    Map<String,T> predict(List<String> items, List<String> assignees, List<String> classCodes);
    Map<String,T> updatePredictions(List<String> items, List<String> assignees, List<String> classCodes);
    File getPredictionsFile();
    void savePredictions(Map<String,T> predictions);
    Map<String,T> loadPredictions();
    void runPipeline(boolean rebuildPrerequisites, boolean rebuildDatasets, boolean runModels, boolean forceRecreateModel, int nTrainingEpochs, boolean predictAssets, boolean forceRerunPredictions);
}
