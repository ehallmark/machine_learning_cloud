package models.similarity_models.keyword_embedding_model;

import data_pipeline.models.Word2VecPredictionModel;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 11/21/17.
 */
public class KeywordEmbeddingModel extends Word2VecPredictionModel<INDArray> {
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"keyword_embedding_word2vec_model_data");

    private KeywordEmbeddingPipelineManager pipelineManager;
    public KeywordEmbeddingModel(KeywordEmbeddingPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees) {
        throw new UnsupportedOperationException("This model does not make asset predictions.");
    }

    @Override
    public void train(int nEpochs) {

        if(net==null) {

        }
        SequenceIterator<VocabWord> iterator = pipelineManager.getDatasetManager().getTrainingIterator();
        net.setSequenceIterator(iterator);
        for(int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch: "+(i+1));
            net.fit();
        }
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
