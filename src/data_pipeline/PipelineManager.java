package data_pipeline;

import data_pipeline.vectorize.DatasetManager;

import java.io.Serializable;

/**
 * Created by ehallmark on 11/7/17.
 */
public interface PipelineManager {
    DatasetManager getDatasetManager();
    void loadRawDatasets();
}
