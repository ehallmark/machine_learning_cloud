package models.similarity_models.cpc2vec_model;

import data_pipeline.models.WordVectorPredictionModel;
import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import models.text_streaming.FileTextDataSetIterator;
import org.deeplearning4j.models.embeddings.learning.impl.elements.CBOW;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.sequencevectors.interfaces.VectorsListener;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by ehallmark on 11/21/17.
 */
public class CPC2VecModel extends WordVectorPredictionModel<INDArray> {
    public static final int VECTOR_SIZE = 32;
    private static final int BATCH_SIZE = 512;
    private static Type MODEL_TYPE = Type.Word2Vec;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"cpc2vec_model_data");

    private CPC2VecPipelineManager pipelineManager;
    public CPC2VecModel(CPC2VecPipelineManager pipelineManager, String modelName) {
        super(modelName+MODEL_TYPE,MODEL_TYPE);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        throw new UnsupportedOperationException("This model does not make asset predictions.");
    }

    @Override
    public void train(int nEpochs) {
        Collection<String> words = pipelineManager.getTestWords();
        CPC2VecIterator iterator = pipelineManager.getDatasetManager().getTrainingIterator();

        DefaultTokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new TokenPreProcess() {
            @Override
            public String preProcess(String s) {
                return s;
            }
        });

        int windowSize = 3;
        int minWordFrequency = 3;
        double negativeSampling = -1;
        double sampling = 0.0001;
        //double learningRate = 0.1;
        //double minLearningRate = 0.001;
        double learningRate = 0.01;
        double minLearningRate = 0.001;

        boolean newModel = net == null;
        Word2Vec.Builder builder = new Word2Vec.Builder()
                .seed(41)
                .batchSize(BATCH_SIZE)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(windowSize)
                .layerSize(VECTOR_SIZE)
                .sampling(sampling)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .minLearningRate(minLearningRate)
                .allowParallelTokenization(true)
                .useAdaGrad(true)
                .resetModel(newModel)
                .minWordFrequency(minWordFrequency)
                .tokenizerFactory(tf)
                .workers(Math.max(1,Runtime.getRuntime().availableProcessors()/2))
                .iterations(1)
                .useHierarchicSoftmax(true)
                .elementsLearningAlgorithm(new CBOW<>())
                .setVectorsListeners(Collections.singleton(new CustomWordVectorListener(null,modelName,1000000,words.toArray(new String[]{}))))
                .iterate(iterator);
        if(!newModel) {
            iterator.setRunVocab(false);
            builder = builder
                    .vocabCache(net.vocab())
                    .lookupTable(net.lookupTable());
        }

        net = builder.build();
        ((Word2Vec)net).fit();

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

        Function<Void,Void> afterEpochFunction = (v) -> {
            for (String word : words) {
                Collection<String> lst = getNet().wordsNearest(word, 10);
                System.out.println("10 CPCs closest to '" + word + "': " + lst);
            }
            saveFunction.apply(getNet());
            return null;
        };

        afterEpochFunction.apply(null);
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
