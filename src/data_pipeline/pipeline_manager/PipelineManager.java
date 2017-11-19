package data_pipeline.pipeline_manager;

import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;

import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public interface PipelineManager<T> {
    DataSetManager getDatasetManager();
    void rebuildPrerequisiteData();
    void saveRawDatasets();
    void trainModels(int nEpochs);
    Map<String,T> predict(List<String> items);
    void savePredictions(Map<String,T> predictions);
    Map<String,T> loadPredictions();
    void runPipeline(boolean rebuildPrerequisites, boolean rebuildDatasets, boolean runModels, boolean forceRecreateModel, int nTrainingEpochs, boolean predictAssets);
}
