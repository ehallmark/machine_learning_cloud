package models.similarity_models.word2vec_model;

import data_pipeline.models.WordVectorPredictionModel;
import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by ehallmark on 11/21/17.
 */
public class Word2VecModel extends WordVectorPredictionModel<INDArray> {
    public static final int VECTOR_SIZE = 128;
    private static final int BATCH_SIZE = 512;
    private static Type MODEL_TYPE = Type.Word2Vec;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"word_word2vec_model_data");

    private Word2VecPipelineManager pipelineManager;
    public Word2VecModel(Word2VecPipelineManager pipelineManager, String modelName) {
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

        FileSequenceIterator iterator = new FileSequenceIterator(new FileTextDataSetIterator(FileTextDataSetIterator.Type.TRAIN), nEpochs, afterEpochFunction);
        DefaultTokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new TokenPreProcess() {
            @Override
            public String preProcess(String s) {
                return s;
            }
        });
        if(net==null) {
            net = new Word2Vec.Builder()
                    .seed(41)
                    .batchSize(BATCH_SIZE)
                    .epochs(1) // hard coded to avoid learning rate from resetting
                    .windowSize(8)
                    .layerSize(VECTOR_SIZE)
                    .sampling(0.0001)
                    .negativeSample(-1)
                    .learningRate(0.1)
                    .minLearningRate(0.001)
                    .allowParallelTokenization(false)
                    .useAdaGrad(true)
                    .resetModel(true)
                    .minWordFrequency(5)
                    .tokenizerFactory(tf)
                    .workers(Math.max(1,Runtime.getRuntime().availableProcessors()/2))
                    .iterations(1)
                    .useHierarchicSoftmax(true)
                    .elementsLearningAlgorithm(new SkipGram<>())
                    .iterate(iterator)
                    .build();

        } else {
            // no need to redo vocab
            iterator.setRunVocab(false);
        }

        ((Word2Vec)net).setSentenceIterator(iterator);
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
