package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public class DeepCPC2VecEncodingPipelineManager extends DefaultPipelineManager<MultiDataSetIterator,INDArray>  {

    public static final String MODEL_NAME = "deep_cpc_2_vec_encoding_model";
    public static final File PREDICTION_FILE = new File(Constants.DATA_FOLDER+"deep_cpc_2_vec_encoding_predictions/predictions_map.jobj");
    private static final File INPUT_DATA_FOLDER = new File("deep_cpc_2_vec_encoding_input_data");
    private static final int VECTOR_SIZE = 24;
    protected static final int BATCH_SIZE = 64;
    private static DeepCPC2VecEncodingPipelineManager MANAGER;
    protected String modelName;
    protected WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    @Getter
    protected Word2Vec word2Vec;
    public DeepCPC2VecEncodingPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(INPUT_DATA_FOLDER,PREDICTION_FILE);
        this.word2Vec=word2Vec;
        this.modelName=modelName;
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
    }


    public void initModel(boolean forceRecreateModels) {
        if(model==null) {
            model = new DeepCPC2VecEncodingModel(this,modelName,VECTOR_SIZE);
        }
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadBestModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    protected MultiDataSetIterator getRawIterator(SequenceIterator<VocabWord> iterator, long numDocs, int batch) {
        return new Word2VecToCPCIterator(iterator,numDocs,null,word2Vec,batch,false);
    }

    @Override
    public void rebuildPrerequisiteData() {

    }

    @Override
    public synchronized DataSetManager<MultiDataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            //datasetManager = new PreSaveDataSetManager(dataFolder);
            setDatasetManager();
        }
        return datasetManager;
    }

    public int getBatchSize() {
        return BATCH_SIZE;
    }

    @Override
    protected void splitData() {
        System.out.println("Starting to recreate datasets...");
        // handled by Elasticsearch
    }

    public File getDevFile() {
        return FileTextDataSetIterator.devFile2;
    }

    protected int getMaxSamples() {
        return 20;
    }

    @Override
    protected void setDatasetManager() {
        File baseDir = FileTextDataSetIterator.BASE_DIR;
        File trainFile = new File(baseDir, FileTextDataSetIterator.trainFile.getName());
        File testFile = new File(baseDir, FileTextDataSetIterator.testFile.getName());
        File devFile = new File(baseDir, getDevFile().getName());

        boolean fullText = baseDir.getName().equals(FileTextDataSetIterator.BASE_DIR.getName());
        System.out.println("Using full text: "+fullText);

        WordCPCIterator trainIter = new WordCPCIterator(new FileTextDataSetIterator(trainFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples(), fullText);
        WordCPCIterator testIter = new WordCPCIterator(new FileTextDataSetIterator(testFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples(), fullText);
        WordCPCIterator devIter = new WordCPCIterator(new FileTextDataSetIterator(devFile),1,wordCPC2VecPipelineManager.getCPCMap(),1,getMaxSamples(), fullText);

        trainIter.setRunVocab(false);
        testIter.setRunVocab(false);
        devIter.setRunVocab(false);

        long numDocs = Database.getAllPatentsAndApplications().size()*3;

        datasetManager = new NoSaveDataSetManager<>(
                getRawIterator(trainIter,numDocs,getBatchSize()),
                getRawIterator(testIter,numDocs, 1024),
                getRawIterator(devIter,numDocs, 1024)
        );
    }


    public static synchronized DeepCPC2VecEncodingPipelineManager getOrLoadManager() {
        if(MANAGER==null) {
            Nd4j.setDataType(DataBuffer.Type.DOUBLE);

            String modelName = MODEL_NAME;
            String wordCpc2VecModel = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;


            WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel, -1, -1, -1);
            wordCPC2VecPipelineManager.runPipeline(false, false, false, false, -1, false);

            setLoggingLevel(Level.INFO);
            MANAGER = new DeepCPC2VecEncodingPipelineManager(modelName, (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet(), wordCPC2VecPipelineManager);
        }
        return MANAGER;
    }


    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        boolean rebuildPrerequisites = false;
        int nEpochs = 5;

        DeepCPC2VecEncodingPipelineManager pipelineManager = getOrLoadManager();
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

}
