package models.similarity_models.rnn_to_cpc_vae_model;

import ch.qos.logback.classic.Level;
import data_pipeline.vectorize.PreSaveDataSetManager;
import models.similarity_models.combined_similarity_model.AbstractEncodingModel;
import models.similarity_models.combined_similarity_model.AbstractEncodingPipelineManager;
import models.similarity_models.combined_similarity_model.Word2VecToCPCIterator;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import java.util.Random;

import java.io.File;

public class RNN2CPCVaePipelineManager extends AbstractEncodingPipelineManager {
    public static final String MODEL_NAME = "rnn_2_cpc_vae_model";
    //public static final String MODEL_NAME = "cpc2vec_new_2_vae_vec_encoding_model";

    public static final File PREDICTION_FILE = new File(Constants.DATA_FOLDER+"rnn_2_cpc_vae_model_prediction/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER_ALL = new File("rnn_2_cpc_vae_model_input_data/");
    private static final int VECTOR_SIZE = DeepCPCVariationalAutoEncoderNN.VECTOR_SIZE;
    protected static final int BATCH_SIZE = 1024;
    protected static final int MINI_BATCH_SIZE = 256;
    private static final int MAX_NETWORK_RECURSION = -1;
    public static final int MAX_SAMPLE = 128;
    protected static final Random rand = new Random(235);
    private static RNN2CPCVaePipelineManager MANAGER;
    public RNN2CPCVaePipelineManager(String modelName, Word2Vec word2Vec, DeepCPCVAEPipelineManager deepCPCVAEPipelineManager) {
        super(new File(currentDataFolderName(MAX_NETWORK_RECURSION,MAX_SAMPLE)),PREDICTION_FILE,modelName+MAX_SAMPLE,word2Vec,VECTOR_SIZE,BATCH_SIZE,MINI_BATCH_SIZE,deepCPCVAEPipelineManager);
    }

    public static String currentDataFolderName(int recursion,int sample) {
        return (INPUT_DATA_FOLDER_ALL).getAbsolutePath()+sample+(recursion>0?("_r"+recursion):"");
    }

    public int getMaxSample() {
        return MAX_SAMPLE;
    }

    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new RNN2CPCVaeModel(this,modelName,VECTOR_SIZE);
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

    public static synchronized RNN2CPCVaePipelineManager getOrLoadManager(boolean loadWord2Vec) {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.FLOAT);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = new File("data/reddit_wiki_word2vec.nn").getAbsolutePath();


            Word2Vec word2Vec = null;
            if(loadWord2Vec) word2Vec = WordVectorSerializer.readWord2VecModel(wordCpc2VecModel);

            DeepCPCVAEPipelineManager deepCPCVAEPipelineManager = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME);
            if(loadWord2Vec) deepCPCVAEPipelineManager.runPipeline(false,false,false,false,-1,false);

            setLoggingLevel(Level.INFO);
            MANAGER = new RNN2CPCVaePipelineManager(modelName, word2Vec, deepCPCVAEPipelineManager);
        }
        return MANAGER;
    }

    @Override
    protected void setDatasetManager() {
        File baseDir = FileTextDataSetIterator.BASE_DIR;
        File trainFile = new File(baseDir, FileTextDataSetIterator.trainFile.getName());
        File testFile = new File(baseDir, FileTextDataSetIterator.testFile.getName());
        File devFile = new File(baseDir, getDevFile().getName());

        boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
        System.out.println("Using full text: "+fullText);

        WordCPCIterator trainIter = new WordCPCIterator(new FileTextDataSetIterator(trainFile),1,getMaxSample()*5, fullText);
        WordCPCIterator testIter = new WordCPCIterator(new FileTextDataSetIterator(testFile),1,getMaxSample()*5, fullText);
        WordCPCIterator devIter = new WordCPCIterator(new FileTextDataSetIterator(devFile),1,getMaxSample()*5, fullText);

        trainIter.setRunVocab(false);
        testIter.setRunVocab(false);
        devIter.setRunVocab(false);

        MultiDataSetIterator train =  getRawIterator(trainIter,getBatchSize());
        MultiDataSetIterator test =  getRawIterator(testIter,1024);
        MultiDataSetIterator val =  getRawIterator(devIter,1024);

        PreSaveDataSetManager<MultiDataSetIterator> manager = new PreSaveDataSetManager<>(
                dataFolder,
                train,
                test,
                val,
                true
        );
        manager.setMultiDataSetPreProcessor(getSeedTimeMultiDataSetPreProcessor());

        datasetManager = manager;
    }

    protected MultiDataSetIterator getRawIterator(SequenceIterator<VocabWord> iterator, int batch) {
        return new Word2VecToCPCIterator(iterator,word2Vec,deepCPCVAEPipelineManager.loadPredictions(), batch, MAX_SAMPLE);
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

        rebuildDatasets = runModels && !new File(currentDataFolderName(MAX_NETWORK_RECURSION,MAX_SAMPLE)).exists();

        RNN2CPCVaePipelineManager pipelineManager = getOrLoadManager(rebuildDatasets||runPredictions);
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }
}