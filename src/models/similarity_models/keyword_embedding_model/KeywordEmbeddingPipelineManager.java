package models.similarity_models.keyword_embedding_model;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import models.keyphrase_prediction.KeywordModelRunner;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;

/**
 * Created by ehallmark on 11/21/17.
 */
public class KeywordEmbeddingPipelineManager extends DefaultPipelineManager<INDArray> {
    public KeywordEmbeddingPipelineManager(File dataFolder, File finalPredictionsFile) {
        super(dataFolder, finalPredictionsFile);
    }

    @Override
    public DataSetManager getDatasetManager() {
        return null;
    }

    @Override
    public void rebuildPrerequisiteData() {
        // rerun keyword model
        KeywordModelRunner.main(null);
    }

    @Override
    protected void initModel(boolean forceRecreateModel) {

    }

    @Override
    protected void splitData() {

    }

    @Override
    protected void setDatasetManager() {

    }
}
