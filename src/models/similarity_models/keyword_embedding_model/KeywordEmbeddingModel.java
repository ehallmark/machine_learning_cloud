package models.similarity_models.keyword_embedding_model;

import data_pipeline.models.Word2VecPredictionModel;
import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.learning.impl.sequence.DBOW;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by ehallmark on 11/21/17.
 */
public class KeywordEmbeddingModel extends Word2VecPredictionModel<INDArray> {
    public static final int VECTOR_SIZE = 128;
    private static final int BATCH_SIZE = 128;
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
        Function<SequenceVectors<VocabWord>,Void> saveFunction = sequenceVectors->{
            double score = sequenceVectors.getElementsScore();
            System.out.println("Score: "+score);
            try {
                save(LocalDateTime.now(),score,(Word2Vec)sequenceVectors);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };
        if(net==null) {
            net = new Word2Vec.Builder()
                    .seed(41)
                    .batchSize(BATCH_SIZE)
                    .epochs(1) // hard coded to avoid learning rate from resetting
                    .windowSize(6)
                    .layerSize(VECTOR_SIZE)
                    .sampling(0.01)
                    .negativeSample(-1)
                    .learningRate(0.05)
                    .minLearningRate(0.0001)
                    .useAdaGrad(true)
                    .resetModel(true)
                    .minWordFrequency(30)
                    .workers(Math.max(1,Runtime.getRuntime().availableProcessors()/2))
                    .iterations(1)
                    .useHierarchicSoftmax(true)
                    .trainSequencesRepresentation(false)
                    .trainElementsRepresentation(true)
                    .elementsLearningAlgorithm(new SkipGram<>())
                    .setVectorsListeners(Arrays.asList(
                            new CustomWordVectorListener(saveFunction,"Keyword Embedding Model",100000,null,pipelineManager.getTestWords().toArray(new String[]{}))
                    ))
                    .build();
        }

        SequenceIterator<VocabWord> iterator = pipelineManager.getDatasetManager().getTrainingIterator();
        net.setSequenceIterator(iterator);
        net.fit();
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
