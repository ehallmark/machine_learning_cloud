package models.similarity_models.keyword_embedding_model;

import data_pipeline.models.WordVectorPredictionModel;
import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.glove.Glove;
import org.deeplearning4j.models.sequencevectors.SequenceVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * Created by ehallmark on 11/21/17.
 */
public class KeywordEmbeddingModel extends WordVectorPredictionModel<INDArray> {
    public static final int VECTOR_SIZE = 32;
    private static final int BATCH_SIZE = 128;
    private static Type MODEL_TYPE = Type.Word2Vec;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"keyword_embedding_word2vec_model_data");

    private KeywordEmbeddingPipelineManager pipelineManager;
    public KeywordEmbeddingModel(KeywordEmbeddingPipelineManager pipelineManager, String modelName) {
        super(modelName+MODEL_TYPE,MODEL_TYPE);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        throw new UnsupportedOperationException("This model does not make asset predictions.");
    }

    @Override
    public void train(int nEpochs) {
        Function<WordVectors,Void> saveFunction = sequenceVectors->{
            System.out.println("Saving...");
            double score = 0d;
            try {
                save(LocalDateTime.now(), score, sequenceVectors);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        Collection<String> words = pipelineManager.getTestWords();
        Function<Void,Void> afterEpochFunction = (v) -> {
            for (String word : words) {
                Collection<String> lst = getNet().wordsNearest(word, 10);
                System.out.println("10 Words closest to '" + word + "': " + lst);
            }
            saveFunction.apply(getNet());
            return null;
        };

        FileSequenceIterator iterator = new FileSequenceIterator(pipelineManager.getOnlyWords(),pipelineManager.getNumEpochs(), afterEpochFunction);
        if(net==null) {
            net = new Word2Vec.Builder()
                    .seed(41)
                    .batchSize(BATCH_SIZE)
                    .epochs(1) // hard coded to avoid learning rate from resetting
                    .windowSize(6)
                    .layerSize(VECTOR_SIZE)
                    .sampling(-1)
                    .negativeSample(-1)
                    .learningRate(0.01)
                    .minLearningRate(0.01)
                    .allowParallelTokenization(true)
                    .useAdaGrad(true)
                    .resetModel(true)
                    .minWordFrequency(5)
                    .workers(Math.max(1,Runtime.getRuntime().availableProcessors()/2))
                    .iterations(3)
                    .useHierarchicSoftmax(true)
                    .elementsLearningAlgorithm(new SkipGram<>())
                    .iterate(iterator)
                    .build();
            /*net = new Glove.Builder()
                    .batchSize(BATCH_SIZE)
                    .epochs(1)
                    .layerSize(VECTOR_SIZE)
                    .useAdaGrad(true)
                    .useHierarchicSoftmax(true)
                    .iterate(iterator)
                    .build();*/
        } else {
            // no need to redo vocab
            iterator.setRunVocab(false);
        }

        ((Word2Vec)net).setSequenceIterator(iterator);
        ((Word2Vec)net).fit();
        synchronized (CustomWordVectorListener.class) {
            System.out.println("Everything should be saved.");
        }
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
