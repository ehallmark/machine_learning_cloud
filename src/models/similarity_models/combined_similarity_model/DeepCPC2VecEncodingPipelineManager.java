package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.CombinedFileMultiMinibatchIterator;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/7/17.
 */
public class DeepCPC2VecEncodingPipelineManager extends AbstractEncodingPipelineManager  {

    public static final String MODEL_NAME = "deep_cpc_rnn3_2_vec_encoding_model";
    public static final File PREDICTION_FILE = new File("deep_cpc_2_vec_encoding_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("deep_cpc_all3_vec_encoding_input_data");
    private static final int VECTOR_SIZE = 32;
    protected static final int BATCH_SIZE = 1024;
    protected static final int MINI_BATCH_SIZE = 32;
    private static final int MAX_NETWORK_RECURSION = 2;
    private static int MAX_SAMPLE = 3;
    protected static final Random rand = new Random(235);
    private static DeepCPC2VecEncodingPipelineManager MANAGER;

    public DeepCPC2VecEncodingPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(new File((INPUT_DATA_FOLDER_ALL).getAbsolutePath()+MAX_SAMPLE),PREDICTION_FILE,modelName+MAX_SAMPLE,word2Vec,VECTOR_SIZE,BATCH_SIZE,MINI_BATCH_SIZE,MAX_SAMPLE,wordCPC2VecPipelineManager);
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new DeepCPC2VecEncodingModel(this,modelName,VECTOR_SIZE);
        }
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
    protected MultiDataSetPreProcessor getMultiDataSetPreProcessor() {
        Random rand = new Random(59);
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                ComputationGraph encoder = ((DeepCPC2VecEncodingModel) model).getVaeNetwork();
                INDArray newFeatures = dataSet.getFeatures(0);
                int r = MAX_NETWORK_RECURSION >= 0 ? rand.nextInt(MAX_NETWORK_RECURSION) : 0;
                for (int i = 0; i < r; i++) {
                    newFeatures = encoder.output(false, dataSet.getFeatures(0))[0];
                }
                dataSet.setFeatures(0, newFeatures);
                dataSet.setLabels(dataSet.getFeatures());
                dataSet.setLabelsMaskArray(null);
                dataSet.setFeaturesMaskArrays(null);
            }
        };
    }

    public static synchronized DeepCPC2VecEncodingPipelineManager getOrLoadManager(boolean loadWord2Vec) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;


            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            if(loadWord2Vec) wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            setLoggingLevel(Level.INFO);
            MANAGER = new DeepCPC2VecEncodingPipelineManager(modelName, loadWord2Vec ? (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet() : null, wordCPC2VecPipelineManager);
        }
        return MANAGER;
    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        setCudaEnvironment();

        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        int nEpochs = 3;

        rebuildDatasets = runModels && !new File(INPUT_DATA_FOLDER_ALL.getAbsolutePath()+MAX_SAMPLE).exists();

        DeepCPC2VecEncodingPipelineManager pipelineManager = getOrLoadManager(rebuildDatasets);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
