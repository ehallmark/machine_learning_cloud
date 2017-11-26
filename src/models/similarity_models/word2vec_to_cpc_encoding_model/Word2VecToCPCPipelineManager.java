package models.similarity_models.word2vec_to_cpc_encoding_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.models.TimeDensityModel;
import models.keyphrase_prediction.stages.Stage1;
import models.keyphrase_prediction.stages.ValidWordStage;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.keyword_encoding_model.KeywordEncodingPipelineManager;
import models.similarity_models.word2vec_model.Word2VecModel;
import models.similarity_models.word2vec_model.Word2VecPipelineManager;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/7/17.
 */
public class Word2VecToCPCPipelineManager extends DefaultPipelineManager<DataSetIterator,INDArray> {
    public static final String MODEL_NAME = "word2vec_to_cpc_encoder";
    public static final File currentVocabMapFile = new File(Constants.DATA_FOLDER+"word2vec_to_cpc_encoding_word_idx_map.jobj");
    private static final int BATCH_SIZE = 128;
    private static final File INPUT_DATA_FOLDER = new File("word2vec_to_cpc_encoding_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"word2vec_to_cpc_encoding_predictions/predictions_map.jobj");
    private String modelName;
    private Map<String,INDArray> assetToEncodingMap;
    private CPCVAEPipelineManager previousManager;
    @Getter
    private Set<String> onlyWords;
    @Getter
    private Word2Vec word2Vec;
    private Map<String,String> stemToBestPhraseMap;
    public Word2VecToCPCPipelineManager(String modelName, Set<String> onlyWords, Map<String,String> stemToBestPhraseMap, Word2Vec word2Vec, CPCVAEPipelineManager previousManager) {
        super(INPUT_DATA_FOLDER,PREDICTION_DATA_FILE);
        this.word2Vec=word2Vec;
        this.modelName=modelName;
        this.stemToBestPhraseMap=stemToBestPhraseMap;
        this.onlyWords=onlyWords;
        this.previousManager=previousManager;
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new Word2VecToCPCEncodingNN(this, modelName);
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
    public void rebuildPrerequisiteData() {

    }


    @Override
    public synchronized DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            datasetManager = new PreSaveDataSetManager(dataFolder);
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

    @Override
    protected void setDatasetManager() {
        File trainFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.trainFile.getName());
        File testFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.testFile.getName());
        File devFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.devFile2.getName());

        LabelAwareIterator trainIter = new FileTextDataSetIterator(trainFile);
        LabelAwareIterator testIter = new FileTextDataSetIterator(testFile);
        LabelAwareIterator devIter = new FileTextDataSetIterator(devFile);

        datasetManager = new PreSaveDataSetManager(dataFolder,
                getRawIterator(trainIter),
                getRawIterator(testIter),
                getRawIterator(devIter)
        );
    }

    protected Map<String,INDArray> getAssetToEncodingMap() {
        if(assetToEncodingMap==null) {
            assetToEncodingMap = previousManager.loadPredictions();
        }
        return assetToEncodingMap;
    }


    protected DataSetIterator getRawIterator(LabelAwareIterator iterator) {
        return new Word2VecToCPCIterator(iterator,getAssetToEncodingMap(),onlyWords,stemToBestPhraseMap,word2Vec,BATCH_SIZE,false,false,false);
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false; // NO PREDICTIONS FOR THIS MODEL
        boolean rebuildPrerequisites = false;

        int nEpochs = 5;
        String modelName = MODEL_NAME;
        String cpcEncodingModel = CPCVAEPipelineManager.MODEL_NAME;
        String word2VecModelName = Word2VecPipelineManager.MODEL_NAME;

        // get latest keywords
        ValidWordStage stage5 = new ValidWordStage(null,new TimeDensityModel());
        stage5.run(false);
        Map<String,String> stemToBestPhraseMap = stage5.get().stream().collect(Collectors.toMap(stem->stem.toString(),stem->stem.getBestPhrase()));
        Set<String> onlyWords = new HashSet<>(stemToBestPhraseMap.keySet());

        Word2VecModel word2VecModel = new Word2VecModel(new Word2VecPipelineManager(word2VecModelName,-1), word2VecModelName);
        word2VecModel.loadMostRecentModel();

        setLoggingLevel(Level.INFO);
        Word2VecToCPCPipelineManager pipelineManager = new Word2VecToCPCPipelineManager(modelName, onlyWords, stemToBestPhraseMap, (Word2Vec)word2VecModel.getNet(), new CPCVAEPipelineManager(cpcEncodingModel));

        pipelineManager.runPipeline(rebuildPrerequisites,rebuildDatasets,runModels,forceRecreateModels,nEpochs,runPredictions);
    }

    public static void setLoggingLevel(Level level) {
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.setLevel(level);
        } catch (Exception e) {
            System.out.println("Error setting log level: "+e.getMessage());
        }
    }
}
