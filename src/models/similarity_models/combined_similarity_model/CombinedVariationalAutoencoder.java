package models.similarity_models.combined_similarity_model;

import data_pipeline.optimize.nn_optimization.NNOptimizer;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.LossFunctionWrapper;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedVariationalAutoencoder extends AbstractCombinedSimilarityModel<MultiLayerNetwork> {
    public static final String VAE_NETWORK = "vaeNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "combined_similarity_vae_data");

    MultiLayerNetwork vaeNetwork;
    int hiddenLayerSize = 96;
    int input1 = 32;
    int input2 = 32;
    int numHiddenEncodings = 5;
    int numHiddenDecodings = 5;

    public CombinedVariationalAutoencoder(CombinedSimilarityVAEPipelineManager pipelineManager, String modelName) {
        super(pipelineManager,MultiLayerNetwork.class,modelName);
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    protected Map<String, MultiLayerNetwork> buildNetworksForTraining() {
        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        int[] hiddenLayerEncoder = new int[numHiddenEncodings];
        int[] hiddenLayerDecoder = new int[numHiddenDecodings];
        for(int i = 0; i < numHiddenEncodings; i++) {
            hiddenLayerEncoder[i]=hiddenLayerSize;
        }
        for(int i = 0; i < numHiddenDecodings; i++) {
            hiddenLayerDecoder[i]=hiddenLayerSize;
        }


        MultiLayerConfiguration vaeNetworkConf =  new NeuralNetConfiguration.Builder()
                .seed(235)
                .learningRate(0.001)
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
                        .activation(Activation.TANH)
                        .pzxActivationFunction(Activation.IDENTITY)
                        .lossFunction(Activation.IDENTITY,lossFunction)
                        .nIn(input1+input2)
                        .nOut((input1+input2)/2)
                        .build()
                )
                .pretrain(true).backprop(false).build();

        vaeNetwork = new MultiLayerNetwork(vaeNetworkConf);

        vaeNetwork.init();

        //syncParams(wordCpc2Vec,cpcVecNet,encodingIdx);

        Map<String,MultiLayerNetwork> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());
        nameToNetworkMap.put(VAE_NETWORK,vaeNetwork);
        return nameToNetworkMap;
    }

    @Override
    protected Map<String, MultiLayerNetwork> updateNetworksBeforeTraining(Map<String, MultiLayerNetwork> networkMap) {
        double newLearningRate = 0.0001;
        double newRegularization = -1;
        vaeNetwork = NNRefactorer.updateNetworkRegularization(NNRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(VAE_NETWORK),newLearningRate,false),newRegularization>0,newRegularization,false);
        Map<String,MultiLayerNetwork> updates = Collections.synchronizedMap(new HashMap<>());
        updates.put(VAE_NETWORK,vaeNetwork);
        return updates;
    }

    @Override
    protected Function<Void, Double> getTestFunction() {
        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        List<DataSet> validationDataSets = Collections.synchronizedList(new ArrayList<>());
        int valCount = 0;
        while(validationIterator.hasNext()&&valCount<20000) {
            DataSet ds = validationIterator.next();
            validationDataSets.add(ds);
            valCount+=ds.getFeatures().rows();
            //System.gc();
        }

        System.out.println("Num validation datasets: "+validationDataSets.size());
        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) vaeNetwork.getLayer(0);

        return (v) -> {
            System.gc();
            return validationDataSets.stream().mapToDouble(ds->CPCVariationalAutoEncoderNN.test(DEFAULT_LABEL_FUNCTION.apply(ds.getFeatureMatrix(),ds.getLabels()),vae)).average().orElse(Double.NaN);
        };
    }

    @Override
    protected void train(INDArray features, INDArray labels) {
        INDArray vec = DEFAULT_LABEL_FUNCTION.apply(features,labels);
        vaeNetwork.fit(new DataSet(vec,vec));
    }

    @Override
    protected Function<IterationListener, Void> setListenerFunction() {
        return listener -> {
            vaeNetwork.setListeners(listener);
            return null;
        };
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
