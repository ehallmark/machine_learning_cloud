package models.similarity_models.combined_similarity_model;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.AbstractWordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import user_interface.ui_models.filters.AbstractExcludeWithRelatedFilter;

import java.io.File;

/**
 * Created by Evan on 2/12/2018.
 */
public abstract class AbstractEncodingPipelineManager extends DefaultPipelineManager<MultiDataSetIterator,INDArray> {
    protected String modelName;
    protected AbstractWordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    @Getter
    protected Word2Vec word2Vec;
    protected int vectorSize;
    protected int batchSize;
    protected int miniBatchSize;
    protected DeepCPCVAEPipelineManager deepCPCVAEPipelineManager;
    public AbstractEncodingPipelineManager(File dataFolder, File predictionFile, String modelName, Word2Vec word2Vec, int vectorSize, int batchSize, int miniBatchSize, AbstractWordCPC2VecPipelineManager wordCPC2VecPipelineManager, DeepCPCVAEPipelineManager deepCPCVAEPipelineManager) {
        super(dataFolder, predictionFile);
        this.word2Vec=word2Vec;
        this.deepCPCVAEPipelineManager=deepCPCVAEPipelineManager;
        System.out.println("Initializing "+modelName);
        this.miniBatchSize=miniBatchSize;
        this.modelName=modelName;
        this.batchSize=batchSize;
        this.vectorSize=vectorSize;
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
    }

    @Override
    public void rebuildPrerequisiteData() {

    }

    @Override
    public synchronized DataSetManager<MultiDataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(dataFolder,miniBatchSize,true);
            manager.setMultiDataSetPreProcessor(getTrainTimeMultiDataSetPreProcessor());
            datasetManager = manager;
            //setDatasetManager();
        }
        return datasetManager;
    }

    protected MultiDataSetPreProcessor getTrainTimeMultiDataSetPreProcessor() {
        return null;
    }

    protected MultiDataSetPreProcessor getSeedTimeMultiDataSetPreProcessor() {
        return null;
    }

    public int getBatchSize() {
        return batchSize;
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        // handled by Elasticsearch
    }

    public abstract File getDevFile();


}
