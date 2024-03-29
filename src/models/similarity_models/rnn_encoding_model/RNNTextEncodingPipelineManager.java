package models.similarity_models.rnn_encoding_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import seeding.google.word2vec.Word2VecManager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Created by ehallmark on 11/7/17.
 */
public class RNNTextEncodingPipelineManager extends DefaultPipelineManager<MultiDataSetIterator,INDArray> {
    public static final int BATCH_SIZE = 1048;
    public static final int MAX_SEQUENCE_LENGTH = 32;
    public static final int MINI_BATCH_SIZE = 128;
    public static final int VECTOR_SIZE = 64;
    public static final String MODEL_NAME256 = "rnn_large_text_encoding_model256";
    public static final File PREDICTION_FILE = new File(Constants.DATA_FOLDER+"rnn_text_encoding_model256_prediction/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("rnn_text_encoding_model256_input_data/");


    private String modelName;
    @Getter
    private int encodingSize;
    @Getter
    private Word2Vec word2Vec;
    private int word2VecSize;
    private RNNTextEncodingPipelineManager(String modelName, Word2Vec word2Vec, int word2VecSize, int encodingSize) {
        super(INPUT_DATA_FOLDER_ALL, PREDICTION_FILE);
        this.word2Vec=word2Vec;
        this.word2VecSize=word2VecSize;
        this.modelName=modelName;
        this.modelName=modelName;
        this.encodingSize=encodingSize;
    }

    @Override
    public void initModel(boolean forceRecreateModels) {
        model = new RNNTextEncodingModel(this,modelName,word2VecSize,encodingSize);
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                ((RNNTextEncodingModel)model).loadModelWithoutDates();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    @Override
    protected void splitData() {
        // do nothing
    }

    private MultiDataSetIterator getIterator(PreparedStatement ps, int limit) {
        return new RNNEncodingIterator(word2Vec,new PostgresSequenceIterator(ps,1,-1,limit),BATCH_SIZE,MAX_SEQUENCE_LENGTH);
    }

    @Override
    protected void setDatasetManager() {
        if (datasetManager == null) {
            Connection conn = Database.getConn();

            int trainSize = 10000000;
            int testSize = 50000;

            int testSuffix = 8;
            int devSuffix = 9;

            try {
                PreparedStatement trainPs = conn.prepareStatement("select abstract from big_query_patent_english_abstract where right(family_id,1)!='" + testSuffix + "' and right(family_id,1)!='" + devSuffix + "' limit "+trainSize);
                PreparedStatement testPs = conn.prepareStatement("select abstract from big_query_patent_english_abstract where right(family_id,1)='" + testSuffix+ "' limit "+testSize);
                PreparedStatement devPs = conn.prepareStatement("select abstract from big_query_patent_english_abstract where right(family_id,1)='" + devSuffix +"' limit "+testSize);
                trainPs.setFetchSize(10);
                testPs.setFetchSize(10);
                devPs.setFetchSize(10);

                MultiDataSetIterator trainIter = getIterator(trainPs, trainSize);
                MultiDataSetIterator testIter = getIterator(testPs, testSize);
                MultiDataSetIterator devIter = getIterator(devPs, testSize);

                datasetManager = new PreSaveDataSetManager<>(
                        dataFolder,
                        trainIter,
                        testIter,
                        devIter,
                        true
                );
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
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

    public static RNNTextEncodingPipelineManager getOrLoadManager(boolean loadWord2Vec) {
        String modelName = MODEL_NAME256;

        int encodingSize = VECTOR_SIZE;
        Word2Vec word2Vec = loadWord2Vec ? Word2VecManager.getOrLoadManager() : null;

        setCudaEnvironment();
        setLoggingLevel(Level.INFO);

        RNNTextEncodingPipelineManager pipelineManager = new RNNTextEncodingPipelineManager(modelName, word2Vec, 256, encodingSize);
        return pipelineManager;
    }


    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 5;

        setCudaEnvironment();
        setLoggingLevel(Level.INFO);

        RNNTextEncodingPipelineManager pipelineManager = getOrLoadManager(true);
        try {
            pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }

}
