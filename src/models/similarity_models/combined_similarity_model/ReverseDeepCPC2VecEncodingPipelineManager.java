package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import data_pipeline.vectorize.PreSaveDataSetManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * Created by ehallmark on 11/7/17.
 */
public class ReverseDeepCPC2VecEncodingPipelineManager extends AbstractEncodingPipelineManager  {

    public static final String MODEL_NAME = "reverse_deep_cpc_rnn3_2_vec_encoding_model";
    public static final File PREDICTION_FILE = new File("reverse_deep_cpc_2_vec_encoding_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("reverse_deep_cpc_all_vec_encoding_input_data");
    private static final int VECTOR_SIZE = 32;
    protected static final int BATCH_SIZE = 1024;
    protected static final int MINI_BATCH_SIZE = 32;
    private static int MAX_SAMPLE = 2;
    protected static final Random rand = new Random(235);
    private static ReverseDeepCPC2VecEncodingPipelineManager MANAGER;
    protected final DeepCPC2VecEncodingPipelineManager encodingPipelineManager;

    public ReverseDeepCPC2VecEncodingPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, DeepCPC2VecEncodingPipelineManager encodingPipelineManager) {
        super(new File((INPUT_DATA_FOLDER_ALL).getAbsolutePath()+MAX_SAMPLE),PREDICTION_FILE,modelName+MAX_SAMPLE,word2Vec,VECTOR_SIZE,BATCH_SIZE,MINI_BATCH_SIZE,MAX_SAMPLE,wordCPC2VecPipelineManager);
        this.encodingPipelineManager=encodingPipelineManager;
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new ReverseDeepCPC2VecEncodingModel(this,modelName,VECTOR_SIZE);
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

    public static synchronized ReverseDeepCPC2VecEncodingPipelineManager getOrLoadManager(boolean loadWord2Vec) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;


            DeepCPC2VecEncodingPipelineManager encodingPipelineManager = DeepCPC2VecEncodingPipelineManager.getOrLoadManager(false);
            encodingPipelineManager.runPipeline(false,false,false,false,-1,false);
            encodingPipelineManager.initModel(false);
            ((DeepCPC2VecEncodingModel)encodingPipelineManager.getModel()).updateNetworksBeforeTraining(null);

            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            if(loadWord2Vec) wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            setLoggingLevel(Level.INFO);
            MANAGER = new ReverseDeepCPC2VecEncodingPipelineManager(modelName, loadWord2Vec ? (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet() : null, wordCPC2VecPipelineManager, encodingPipelineManager);
        }
        return MANAGER;
    }

    @Override
    protected MultiDataSetPreProcessor getSeedTimeMultiDataSetPreProcessor() {
        return new MultiDataSetPreProcessor() {
            @Override
            public void preProcess(org.nd4j.linalg.dataset.api.MultiDataSet dataSet) {
                INDArray features = dataSet.getFeatures(0);

                ComputationGraph encoder = ((DeepCPC2VecEncodingModel) model).getVaeNetwork();
                int[] newShape = features.shape().clone();
                newShape[2]--;
                INDArray newFeatures = Nd4j.create(newShape);
                for(int i = 0; i < maxSample; i++) {
                    INDArray encoding = encoder.output(false, features.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(i)))[0];
                    newFeatures.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(i)).assign(encoding);
                }

                INDArray labels = features.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(maxSample));

                dataSet.setFeatures(0, newFeatures);
                dataSet.setLabels(0,labels);
            }
        };
    }

    @Override
    protected void setDatasetManager() {
        //encodingPipelineManager.setDatasetManager();
        PreSaveDataSetManager<MultiDataSetIterator> preManager = new PreSaveDataSetManager<>(
                // need the dataset file with 1 extra sample
                new File(DeepCPC2VecEncodingPipelineManager.currentDataFolderName(-1,maxSample+1)),
                miniBatchSize,
                true
        );
        preManager.setMultiDataSetPreProcessor(encodingPipelineManager.getTrainTimeMultiDataSetPreProcessor());

        MultiDataSetIterator trainIter = encodingPipelineManager.getDatasetManager().getTrainingIterator();
        MultiDataSetIterator testIter = encodingPipelineManager.getDatasetManager().getTestIterator();
        MultiDataSetIterator valIter = encodingPipelineManager.getDatasetManager().getValidationIterator();

        PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(
                dataFolder,
                trainIter,
                testIter,
                valIter,
                true
        );
        manager.setMultiDataSetPreProcessor(getSeedTimeMultiDataSetPreProcessor());
        datasetManager = manager;
    }


    @Override
    public File getDevFile() {
        return FileTextDataSetIterator.devFile4;
    }


    public static void main(String[] args) {
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

        ReverseDeepCPC2VecEncodingPipelineManager pipelineManager = getOrLoadManager(rebuildDatasets);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
