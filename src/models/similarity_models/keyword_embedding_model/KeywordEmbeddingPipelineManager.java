package models.similarity_models.keyword_embedding_model;

import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import data_pipeline.vectorize.NoSaveDataSetManager;
import data_pipeline.vectorize.PreSaveDataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.KeywordModelRunner;
import models.similarity_models.deep_word_to_cpc_encoding_model.DeepWordToCPCEncodingNN;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/21/17.
 */
public class KeywordEmbeddingPipelineManager extends DefaultPipelineManager<SequenceIterator<VocabWord>,INDArray> {
    public static final String MODEL_NAME = "keyword_embedding_model";
    private static final File INPUT_DATA_FOLDER = new File("keyword_embedding_data");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"keyword_embedding_predictions/predictions_map.jobj");

    private String modelName;
    @Getter
    private Set<String> onlyWords;
    @Getter
    private int numEpochs;
    @Getter
    private List<String> testWords;
    public KeywordEmbeddingPipelineManager(String modelName, Set<String> onlyWords, int numEpochs) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.numEpochs=numEpochs;
        this.modelName=modelName;
        this.onlyWords=onlyWords;
        List<String> onlyWordsList = new ArrayList<>(onlyWords);
        Collections.shuffle(onlyWordsList);
        this.testWords = new ArrayList<>(onlyWordsList.subList(0,Math.min(10,onlyWordsList.size())));
    }

    @Override
    public void rebuildPrerequisiteData() {
        // rerun keyword model
        KeywordModelRunner.main(null);
    }

    protected void initModel(boolean forceRecreateModels) {
        model = new KeywordEmbeddingModel(this, modelName);
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
        // purposefully do nothing
    }


    @Override
    public DataSetManager<SequenceIterator<VocabWord>> getDatasetManager() {
        return null;
    }

    @Override
    protected void setDatasetManager() {

    }



}
