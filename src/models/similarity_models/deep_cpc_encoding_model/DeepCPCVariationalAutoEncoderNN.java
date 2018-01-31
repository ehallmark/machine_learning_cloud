package models.similarity_models.deep_cpc_encoding_model;

import cpc_normalization.CPCHierarchy;
import data_pipeline.helpers.Function2;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by ehallmark on 10/26/17.
 */
public class DeepCPCVariationalAutoEncoderNN extends CPCVariationalAutoEncoderNN {
    public static final int VECTOR_SIZE = 256;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"deep_cpc_deep_vae_nn_model_data");

    public DeepCPCVariationalAutoEncoderNN(DeepCPCVAEPipelineManager pipelineManager, String modelName, int maxCpcDepth) {
        super(pipelineManager,modelName,maxCpcDepth);
    }

    @Override
    public Map<String,Integer> getCpcToIdxMap() {
        if(cpcToIdxMap==null) {
            RecursiveTask<CPCHierarchy> hierarchyTask = new RecursiveTask<CPCHierarchy>() {
                @Override
                protected CPCHierarchy compute() {
                    return pipelineManager.getHierarchy();
                }
            };
            cpcToIdxMap = DeepCPCIndexMap.loadOrCreateMapForDepth(hierarchyTask,maxCPCDepth,((DeepCPCVAEPipelineManager)pipelineManager).getMinCPCOccurrences());
        }
        return cpcToIdxMap;
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }


    @Override
    public void train(int nEpochs) {
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        DataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();
        final int numInputs = getCpcToIdxMap().size();
        final int printIterations = 100;

        if(net==null) {
            //Neural net configuration
            int[] hiddenLayerEncoder = new int[]{
                    2056,
                    2056,
                    2056,
                    2056
            };
            int[] hiddenLayerDecoder = new int[hiddenLayerEncoder.length];
            for(int i = 0; i < hiddenLayerEncoder.length; i++) {
                hiddenLayerDecoder[i] = hiddenLayerEncoder[hiddenLayerEncoder.length-1-i];
            }
            int rngSeed = 69;
            Activation activation = Activation.TANH;
            Nd4j.getRandom().setSeed(rngSeed);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(rngSeed)
                    .learningRate(0.05)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(Updater.RMSPROP).rmsDecay(0.95)
                    //.updater(Updater.ADAM)
                    .miniBatch(true)
                    .weightInit(WeightInit.XAVIER)
                    //.gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                    //.gradientNormalizationThreshold(1d)
                    //.regularization(true).l2(1e-4)
                    .list()
                    .layer(0, new VariationalAutoencoder.Builder()
                            .encoderLayerSizes(hiddenLayerEncoder)
                            .decoderLayerSizes(hiddenLayerDecoder)
                            //.lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE)
                            .activation(activation)
                            .pzxActivationFunction(Activation.IDENTITY)
                            .reconstructionDistribution(new BernoulliReconstructionDistribution(Activation.SIGMOID))
                            .nIn(numInputs)
                            .nOut(VECTOR_SIZE)
                            .build()
                    )
                    .pretrain(true).backprop(false).build();

            net = new MultiLayerNetwork(conf);
            net.init();
        } else {
            //double learningRate1 = 0.01;
            double learningRate2 = 0.001;
            net = NNRefactorer.updateNetworkLearningRate(net,learningRate2,false);
            net = NNRefactorer.updatePretrainAndBackprop(net,true,false,false);
            System.out.println("new learning rates: ");
            net.getLayerWiseConfigurations().getConfs().forEach((conf)->{
                conf.getLearningRateByParam().entrySet().forEach(e->{
                    System.out.println("  "+e.getKey()+": "+e.getValue());
                });
            });
        }

        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);

        System.out.println("Building validation matrix...");
        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        Function<Void,Double> testErrorFunction = (v) -> {
            double total = 0d;
            int count = 0;
            while(validationIterator.hasNext()) {
                INDArray features = validationIterator.next().getFeatures();
                double score = test(features, vae);
                count++;
                total+=score;
            }
            validationIterator.reset();
            return total/count;
        };

        Function<Void,Double> trainErrorFunction = (v) -> {
            return 0d;//test(pipelineManager.getDatasetManager().getTrainingIterator(10000/pipelineManager.getBatchSize()), vae);
        };

        Function2<LocalDateTime,Double,Void> saveFunction = (datetime,score) -> {
            try {
                save(datetime,score);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, stoppingCondition);
        net.setListeners(listener);

        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            try {
                net.fit(trainIter);
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            if(stoppingCondition.get()) {
                break;
            }
            trainIter.reset();
        }
    }



}
