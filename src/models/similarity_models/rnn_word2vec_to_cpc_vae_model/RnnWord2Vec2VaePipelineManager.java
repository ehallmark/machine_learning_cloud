package models.similarity_models.rnn_word2vec_to_cpc_vae_model;

import ch.qos.logback.classic.Level;
import data_pipeline.vectorize.PreSaveDataSetManager;
import models.similarity_models.combined_similarity_model.AbstractEncodingPipelineManager;
import models.similarity_models.combined_similarity_model.Word2VecToCPCIterator;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.rnn_encoding_model.PostgresSequenceIterator;
import models.similarity_models.rnn_encoding_model.RNNEncodingIterator;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Random;

/**
 * Created by ehallmark on 11/7/17.
 */
public class RnnWord2Vec2VaePipelineManager extends AbstractEncodingPipelineManager  {

    public static final String MODEL_NAME = "rnn_word2vec_2_vae_model";
    //public static final String MODEL_NAME = "cpc2vec_new_2_vae_vec_encoding_model";
    public static final File PREDICTION_FILE = new File(Constants.DATA_FOLDER+"rnn_word2vec_2_vae_model_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("rnn_word2vec_2_vae_model_input_data/");
    protected static final int BATCH_SIZE = 1024;
    protected static final int VECTOR_SIZE = DeepCPCVariationalAutoEncoderNN.VECTOR_SIZE;
    protected static final int MINI_BATCH_SIZE = 64;
    private static final int MAX_SEQUENCE_LENGTH = 32;
    protected static final Random rand = new Random(235);
    private static RnnWord2Vec2VaePipelineManager MANAGER;
    public RnnWord2Vec2VaePipelineManager(String modelName, Word2Vec word2Vec) {
        super(new File(currentDataFolderName(MAX_SEQUENCE_LENGTH)),PREDICTION_FILE,modelName+MAX_SEQUENCE_LENGTH,word2Vec,BATCH_SIZE,MINI_BATCH_SIZE,null);
    }

    public static String currentDataFolderName(int sample) {
        return (INPUT_DATA_FOLDER_ALL).getAbsolutePath()+sample;
    }


    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new RnnWord2Vec2VaeModel(this,modelName,256);
        }
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                ((RnnWord2Vec2VaeModel)model).loadModelWithoutDates();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }


    @Override
    protected MultiDataSetPreProcessor getTrainTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(MultiDataSet dataSet) {
                //INDArray labels = dataSet.getLabels(0);
                //INDArray newLabels = Nd4j.zeros(labels.shape()[0],labels.shape()[1],dataSet.getFeatures(0).shape()[2]);
                //newLabels.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(newLabels.shape()[2]-1)).assign(labels);
                //INDArray labelMask = Nd4j.zeros(labels.shape()[0],newLabels.shape()[2]);
                //labelMask.get(NDArrayIndex.all(),NDArrayIndex.point(labelMask.columns()-1)).assign(1);
                //dataSet.setLabelsMaskArray(new INDArray[]{labelMask});
                //dataSet.setLabels(0,newLabels);
                //System.out.println("Label shape: "+ Arrays.toString(newLabels.shape()));
                //dataSet.setLabelsMaskArray(new INDArray[]{});
            }
        };
    }


    @Override
    protected MultiDataSetPreProcessor getSeedTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(MultiDataSet dataSet) {
            }
        };
    }


    @Override
    public File getDevFile() {
        return FileTextDataSetIterator.devFile3;
    }

    public static synchronized RnnWord2Vec2VaePipelineManager getOrLoadManager(boolean loadWord2Vec) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.FLOAT);

            String modelName = MODEL_NAME;
            String word2VecPath = new File("data/word2vec_model.nn256").getAbsolutePath();

            Word2Vec word2Vec = null;
            if(loadWord2Vec) word2Vec = WordVectorSerializer.readWord2VecModel(word2VecPath);

            setLoggingLevel(Level.INFO);
            MANAGER = new RnnWord2Vec2VaePipelineManager(modelName, word2Vec);
        }
        return MANAGER;
    }

    private MultiDataSetIterator getIterator(PreparedStatement ps, int limit) {
        return new RnnToVaeIterator(word2Vec,new PostgresVectorizedSequenceIterator(ps,1,2,limit),BATCH_SIZE,MAX_SEQUENCE_LENGTH,VECTOR_SIZE);
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
                PreparedStatement trainPs = conn.prepareStatement("select abstract,cpc_vae from big_query_patent_english_abstract as a join big_query_embedding1 as e on (a.family_id=e.family_id) where right(a.family_id,1)!='" + testSuffix + "' and right(a.family_id,1)!='" + devSuffix + "' limit "+trainSize);
                PreparedStatement testPs = conn.prepareStatement("select abstract,cpc_vae from big_query_patent_english_abstract as a join big_query_embedding1 as e on (a.family_id=e.family_id) where right(a.family_id,1)='" + testSuffix+ "' limit "+testSize);
                PreparedStatement devPs = conn.prepareStatement("select abstract,cpc_vae from big_query_patent_english_abstract as a join big_query_embedding1 as e on (a.family_id=e.family_id) where right(a.family_id,1)='" + devSuffix +"' limit "+testSize);
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

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        setCudaEnvironment();

        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        int nEpochs = 5;

        rebuildDatasets = runModels && !new File(currentDataFolderName(MAX_SEQUENCE_LENGTH)).exists();

        RnnWord2Vec2VaePipelineManager pipelineManager = getOrLoadManager(rebuildDatasets||runPredictions);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
