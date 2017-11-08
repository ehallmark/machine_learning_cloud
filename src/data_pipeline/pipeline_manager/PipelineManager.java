package data_pipeline.pipeline_manager;

import data_pipeline.vectorize.DatasetManager;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public interface PipelineManager<T> {
    DatasetManager getDatasetManager();
    void saveRawDatasets();
    void trainModels(int nEpochs);
    Map<String,T> predict(List<String> items);
    void savePredictions(Map<String,T> predictions);
    void runPipeline(boolean rebuildDatasets, boolean runModels, boolean forceRecreateModel, int nTrainingEpochs, boolean predictAssets);
}
