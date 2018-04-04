package models.similarity_models.rnn_encoding_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPCHierarchy;
import data_pipeline.models.ComputationGraphPredictionModel;
import data_pipeline.models.TrainablePredictionModel;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import models.similarity_models.cpc_encoding_model.CPCDataSetIterator;
import models.similarity_models.deep_cpc_encoding_model.DeeperCPCIndexMap;
import models.similarity_models.word_cpc_2_vec_model.ParagraphCPC2VecModel;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/7/17.
 */
public class RNNTextEncodingPipelineManager extends DefaultPipelineManager<MultiDataSetIterator,INDArray> {
    public static final int BATCH_SIZE = 1024;
    public static final int MAX_SEQUENCE_LENGTH = 128;
    public static final int MINI_BATCH_SIZE = 32;
    public static final String MODEL_NAME256 = "rnn_text_encoding_model256";

    private String modelName;
    private int encodingSize;
    private Word2Vec word2Vec;
    private RNNTextEncodingPipelineManager(String modelName, File dataFolder, File finalPredictionsFile, Word2Vec word2Vec, int encodingSize) {
        super(dataFolder, finalPredictionsFile);
        this.word2Vec=word2Vec;
        this.modelName=modelName;
        this.encodingSize=encodingSize;
    }

    @Override
    protected void initModel(boolean forceRecreateModels) {
        model = new RNNTextEncodingModel(this,modelName,word2Vec.getLayerSize(),encodingSize);
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
        // do nothing
    }

    private MultiDataSetIterator getIterator(File[] files, int limit) {
        return new RNNEncodingIterator(word2Vec,new ZippedFileSequenceIterator(files,limit),BATCH_SIZE,MAX_SEQUENCE_LENGTH);
    }

    @Override
    protected void setDatasetManager() {
        if (datasetManager == null) {
            File[] trainFiles = new File[]{}; // TODO put correct file locations
            File[] testFiles = new File[]{};
            File[] devFiles = new File[]{};


            MultiDataSetIterator trainIter = getIterator(trainFiles,-1);
            MultiDataSetIterator testIter = getIterator(testFiles,50000);
            MultiDataSetIterator devIter = getIterator(devFiles,50000);


            datasetManager = new PreSaveDataSetManager<>(
                    dataFolder,
                    trainIter,
                    testIter,
                    devIter,
                    true
            );
        }
    }

    @Override
    public DataSetManager<MultiDataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new PreSaveDataSetManager<>(dataFolder, MINI_BATCH_SIZE, true);
        }
        return datasetManager;
    }

    @Override
    public void rebuildPrerequisiteData() {

    }


    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 5;
        String modelName = MODEL_NAME256;

        int encodingSize = 128;
        int inputSize = 256;
        String word2VecPath = "giant_wordvectors"+inputSize;
        String dataDirStr = "rnn_text_encoding_model_data_directory";
        String predictionsDirStr = "rnn_text_encoding_model_predictions"+inputSize;

        Word2Vec word2Vec = WordVectorSerializer.readWord2VecModel(word2VecPath);

        setCudaEnvironment();
        setLoggingLevel(Level.INFO);

        File dataDir = new File(dataDirStr);
        File predictionsDir = new File(predictionsDirStr);

        RNNTextEncodingPipelineManager pipelineManager = new RNNTextEncodingPipelineManager(modelName,dataDir,predictionsDir,word2Vec,encodingSize);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
