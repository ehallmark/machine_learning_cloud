package models.similarity_models.combined_similarity_model;

import ch.qos.logback.classic.Level;
import data_pipeline.vectorize.NoSaveDataSetManager;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

/**
 * Created by ehallmark on 11/7/17.
 */
public class RecurrentWordCPC2VecPipelineManager extends AbstractCombinedSimilarityPipelineManager {
    public static final String MODEL_NAME = "recurrent_word_cpc_2_vec_model";
    private static final File INPUT_DATA_FOLDER = new File("combined_similarity_model_input_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"recurrent_word_cpc_2_vec_model_predictions/predictions_map.jobj");

    private static Map<String,INDArray> combinedVaePredictionsMap;
    public RecurrentWordCPC2VecPipelineManager(String modelName, Word2Vec word2Vec, WordCPC2VecPipelineManager wordCPC2VecPipelineManager, CPCVAEPipelineManager cpcvaePipelineManager) {
        super(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE,modelName,word2Vec,wordCPC2VecPipelineManager,cpcvaePipelineManager);

    }

    public void initModel(boolean forceRecreateModels) {
        CombinedSimilarityVAEPipelineManager vaePipelineManager = new CombinedSimilarityVAEPipelineManager(CombinedSimilarityVAEPipelineManager.MODEL_NAME,word2Vec,wordCPC2VecPipelineManager,cpcvaePipelineManager);
        combinedVaePredictionsMap = vaePipelineManager.loadPredictions();

        if(model==null) model = new RecurrentWordCPC2VecModel(this,modelName);
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
    public int getBatchSize() {
        return 16;
    }



    @Override
    protected DataSetIterator getRawIterator(SequenceIterator<VocabWord> iterator, long numDocs, int batch) {
        return new RecurrentWord2VecIterator(iterator,combinedVaePredictionsMap,word2Vec,batch,getMaxSamples(), Collections.synchronizedSet(new HashSet<>(wordCPC2VecPipelineManager.getOrLoadCPCVectors().keySet())));
    }

    @Override
    public int getMaxSamples() {
        return 16;
    }


    @Override
    public File getDevFile() {
        return FileTextDataSetIterator.devFile3;
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false; // NO PREDICTIONS FOR THIS MODEL
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;
        String modelName = MODEL_NAME;
        String cpcEncodingModel = CPCVAEPipelineManager.MODEL_NAME;
        String wordCpc2VecModel = WordCPC2VecPipelineManager.SMALL_MODEL_NAME;

        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(wordCpc2VecModel,-1,-1,-1);
        wordCPC2VecPipelineManager.runPipeline(false,false,false,false,-1,false);

        setLoggingLevel(Level.INFO);
        RecurrentWordCPC2VecPipelineManager pipelineManager = new RecurrentWordCPC2VecPipelineManager(modelName, (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet(), wordCPC2VecPipelineManager, new CPCVAEPipelineManager(cpcEncodingModel));
        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }


}