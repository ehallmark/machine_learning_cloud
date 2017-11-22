package models.similarity_models.keyword_encoding_model;

import ch.qos.logback.classic.Level;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.models.TimeDensityModel;
import models.keyphrase_prediction.stages.Stage1;
import models.keyphrase_prediction.stages.ValidWordStage;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.keyword_embedding_model.KeywordEmbeddingPipelineManager;
import models.text_streaming.BOWAutoEncoderIterator;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.text.documentiterator.LabelAwareIterator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public class KeywordEncodingPipelineManager extends DefaultPipelineManager<DataSetIterator,INDArray> {
    public static final String MODEL_NAME = "keyword_encoding_model";
    private static final File INPUT_DATA_FOLDER = new File("keyword_encoding_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"keyword_encoding_predictions/predictions_map.jobj");

    private String modelName;
    @Getter
    private Set<String> onlyWords;
    public KeywordEncodingPipelineManager(String modelName, Set<String> onlyWords) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.modelName=modelName;
        this.onlyWords=onlyWords;
    }

    @Override
    public void rebuildPrerequisiteData() {
        // rerun keyword model
        KeywordModelRunner.main(null);
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new KeywordEncodingModel(this, modelName);
        if(!forceRecreateModels) {
            System.out.println("Warning: Loading previous model.");
            try {
                model.loadBestModel();
            } catch(Exception e) {
                System.out.println("Error loading previous model: "+e.getMessage());
            }
        }
    }

    @Override
    protected void splitData() {
        // purposefully do nothing
    }


    @Override
    public DataSetManager<DataSetIterator> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
            int batch = KeywordEncodingModel.BATCH_SIZE;
            Map<String,Integer> wordToIdxMap = WordIndexMap.loadOrCreateWordIdxMap(onlyWords);

            File trainFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.trainFile.getName());
            File testFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.testFile.getName());
            File devFile = new File(Stage1.getTransformedDataFolder(), FileTextDataSetIterator.devFile2.getName());

            LabelAwareIterator trainIter = new FileTextDataSetIterator(trainFile);
            LabelAwareIterator testIter = new FileTextDataSetIterator(testFile);
            LabelAwareIterator devIter = new FileTextDataSetIterator(devFile);
            datasetManager = new NoSaveDataSetManager<>(
                    new BOWAutoEncoderIterator(batch,trainIter,wordToIdxMap),
                    new BOWAutoEncoderIterator(batch,testIter,wordToIdxMap),
                    new BOWAutoEncoderIterator(batch,devIter,wordToIdxMap)
            );
        }
    }

    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = true;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 10;
        String modelName = MODEL_NAME;

        setLoggingLevel(Level.INFO);

        ValidWordStage stage5 = new ValidWordStage(null,new TimeDensityModel());
        stage5.run(false);

        Set<String> onlyWords = stage5.get().stream().map(stem->stem.toString()).collect(Collectors.toSet());
        KeywordEncodingPipelineManager pipelineManager = new KeywordEncodingPipelineManager(modelName,onlyWords);

        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
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
