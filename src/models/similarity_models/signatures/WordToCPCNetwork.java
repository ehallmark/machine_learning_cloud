package models.similarity_models.signatures;

import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by Evan on 10/29/2017.
 */
public class WordToCPCNetwork {
    private static final File wordIdxMapFile = new File(Constants.DATA_FOLDER+"word_to_cpc_network_idx_map.jobj");
    private static final File modelFile = new File(Constants.DATA_FOLDER+"word_to_cpc_network_neural_model.jobj");

    public static MultiLayerNetwork loadNet() throws IOException{
        return ModelSerializer.restoreMultiLayerNetwork(modelFile,true);
    }
    public static void main(String[] args) throws Exception {
        final int batchSize = 32;
        final int sampling = 5000000;
        final int vocabSampling = 1000000;
        final int seed = 69;
        final int minWordCount = 10;
        final boolean rerunVocab = false;
        final int printIterations = 50;
        final int nEpochs = 5;
        final boolean binarize = false;
        final boolean normalize = true;
        final boolean probability = true;
        WordToCPCIterator iterator = new WordToCPCIterator(null, batchSize, vocabSampling, seed, minWordCount, binarize, normalize, probability);

        Map<String,Integer> idxMap;
        if(!rerunVocab&&wordIdxMapFile.exists()) {
            System.out.println("Warning: Using existing vocab index file.");
            idxMap = (Map<String,Integer>)Database.tryLoadObject(wordIdxMapFile);
            iterator.setWordToIdxMap(idxMap);
        } else {
            // start with vocab;
            System.out.println("Starting to build vocabulary...");
            iterator.buildVocabMap();
            System.out.println("Built vocab.");
            idxMap = iterator.getWordToIdxMap();
            System.out.println("Starting to save vocab...");
            Database.trySaveObject(idxMap,wordIdxMapFile);
            System.out.println("Finished saving vocab.");
        }

        iterator.setLimit(sampling);

        // get vocab
        final int vocabSize = iterator.getWordToIdxMap().size();
        final int hiddenLayerSize = 800;
        final int outputSize = SignatureSimilarityModel.VECTOR_SIZE;

        Nd4j.getRandom().setSeed(seed);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .activation(Activation.TANH)
                .updater(Updater.RMSPROP)
                .rmsDecay(0.95)
                .seed(seed)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.01)
                .miniBatch(true)
                .weightInit(WeightInit.XAVIER)
                .regularization(true).l2(1e-4)
                .dropOut(0.5)
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(vocabSize)
                        .nOut(hiddenLayerSize)
                        .build()
                ).layer(1, new DenseLayer.Builder()
                        .nIn(hiddenLayerSize)
                        .nOut(hiddenLayerSize)
                        .build()
                ).layer(2, new OutputLayer.Builder()
                        .lossFunction(LossFunctions.LossFunction.MSE)
                        .activation(Activation.SIGMOID)
                        .nIn(hiddenLayerSize)
                        .nOut(outputSize)
                        .build()
                ).build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        iterator.setNetwork(net); // IMPORTANT!!!

        System.out.println("Getting test iterator..."); {
            double test = iterator.test();
            System.out.println("Initial error: "+test);
        }
        System.out.println("Finished.");

        Function<Void,Double> testFunction = (v) -> {
            return iterator.test();
        };

        Function<Void,Double> trainFunction = (v) -> {
            return 0d;
        };

        Function<LocalDateTime,Void> saveFunction = v -> {
            save(net);
            return null;
        };
        AtomicBoolean isSaved = new AtomicBoolean(false);
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        IterationListener listener = new DefaultScoreListener(printIterations, testFunction, trainFunction, saveFunction, stoppingCondition);
        net.setListeners(listener);

        System.out.println("Starting to train data...");
        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            //net.fit(trainIter);
            try {
                iterator.trainNetwork();
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            if(stoppingCondition.get()) {
                break;
            }
            if(!isSaved.get()) {
                try {
                    save(net);
                    // allow more saves after this
                    isSaved.set(false);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("Saving results...");
        save(net);
        System.out.println("Finished saving.");
    }

    private static void save(MultiLayerNetwork net) {
        try {
            ModelSerializer.writeModel(net, modelFile, true);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
