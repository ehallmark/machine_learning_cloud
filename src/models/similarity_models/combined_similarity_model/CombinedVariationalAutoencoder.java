package models.similarity_models.combined_similarity_model;

import data_pipeline.optimize.nn_optimization.NNOptimizer;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
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
    int hiddenLayerSize = 64;
    int input1 = 32;
    int input2 = 32;

    public CombinedVariationalAutoencoder(CombinedSimilarityVAEPipelineManager pipelineManager, String modelName) {
        super(pipelineManager,MultiLayerNetwork.class,modelName);
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    protected Map<String, MultiLayerNetwork> buildNetworksForTraining() {
        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(2352)
                .iterations(1)
                .learningRate(0.001)
                .updater(Updater.RMSPROP)
                .miniBatch(true)
                .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(input1+input2).nOut(input1+input2).activation(Activation.SIGMOID).build())
                .layer(1, new RBM.Builder().nIn(input1+input2).nOut(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(2, new RBM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(3, new RBM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(4, new RBM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(5, new RBM.Builder().nIn(hiddenLayerSize).nOut(32).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build()) //encoding stops
                .layer(6, new RBM.Builder().nIn(32).nOut(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build()) //decoding starts
                .layer(7, new RBM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(8, new RBM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(9, new RBM.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE).build())
                .layer(10, new OutputLayer.Builder(LossFunctions.LossFunction.COSINE_PROXIMITY).activation(Activation.TANH).nIn(hiddenLayerSize).nOut(input1+input2).build())
                .pretrain(true).backprop(true)
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        vaeNetwork = new MultiLayerNetwork(conf);

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

        return (v) -> {
            System.gc();
            return validationDataSets.stream().mapToDouble(ds->test(vaeNetwork,ds.getFeatures(),ds.getLabels())).average().orElse(Double.NaN);
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
