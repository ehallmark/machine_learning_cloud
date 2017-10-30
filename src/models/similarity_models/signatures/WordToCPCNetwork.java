package models.similarity_models.signatures;

import com.google.common.util.concurrent.AtomicDouble;
import models.similarity_models.signatures.scorers.DefaultScoreListener;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

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
        final int batchSize = 128;
        final int sampling = 5000000;
        final int vocabSampling = 1000000;
        final int seed = 69;
        final int minWordCount = 10;
        final boolean rerunVocab = true;

        WordToCPCIterator iterator = new WordToCPCIterator(batchSize, vocabSampling, seed, minWordCount);

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
        final int hiddenLayerSize = 512;
        final int outputSize = SignatureSimilarityModel.VECTOR_SIZE;

        Nd4j.getRandom().setSeed(seed);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .activation(Activation.LEAKYRELU)
                .updater(Updater.RMSPROP)
                .rmsDecay(0.95)
                .seed(seed)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.01)
                .miniBatch(true)
                .weightInit(WeightInit.XAVIER)
                .regularization(true).l2(1e-4)
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(vocabSize)
                        .nOut(hiddenLayerSize)
                        .build()
                ).layer(1, new DenseLayer.Builder()
                        .nIn(hiddenLayerSize)
                        .nOut(hiddenLayerSize)
                        .build()
                ).layer(2, new DenseLayer.Builder()
                        .nIn(hiddenLayerSize)
                        .nOut(hiddenLayerSize)
                        .build()
                ).layer(3, new OutputLayer.Builder()
                        .lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .activation(Activation.TANH)
                        .nIn(hiddenLayerSize)
                        .nOut(outputSize)
                        .build()
                ).build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        System.out.println("Getting test iterator..."); {
            iterator.getTestIterator();
        }
        System.out.println("Finished.");

        final int printIterations = 500;
        Function<Void,Double> testFunction = (v) -> {
            AtomicDouble totalError = new AtomicDouble(0d);
            AtomicInteger cnt = new AtomicInteger(0);
            Iterator<DataSet> dataStream = iterator.getTestIterator();
            while(dataStream.hasNext()) {
                DataSet ds = dataStream.next();
                INDArray actualOutput = ds.getLabels();
                INDArray modelOutput = net.activateSelectedLayers(0,net.getnLayers()-1,ds.getFeatures());
                for(int i = 0; i < actualOutput.rows(); i++) {
                    double score = Transforms.cosineSim(modelOutput,actualOutput);
                    if(Double.isNaN(score)) score = -1d;
                    totalError.getAndAdd(1d-score);
                    cnt.getAndIncrement();
                }

            }
            if(cnt.get()>0) {
                totalError.set(totalError.get()/cnt.get());
            }
            return totalError.get();
        };

        Function<Void,Void> saveFunction = v -> {
            save(net);
            return null;
        };
        AtomicBoolean isSaved = new AtomicBoolean(false);
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        IterationListener listener = new DefaultScoreListener(printIterations, testFunction, saveFunction, isSaved, stoppingCondition);
        net.setListeners(listener);

        System.out.println("Starting to train data...");
        final int nEpochs = 5;
        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            //net.fit(trainIter);
            try {
                net.fit(iterator);
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
            iterator.reset();
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
