package models.similarity_models.combined_similarity_model;

import data_pipeline.optimize.nn_optimization.CGRefactorer;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.LossFunctionWrapper;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.graph.ComputationGraph;
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
public class CombinedVariationalAutoencoder extends AbstractCombinedSimilarityModel<ComputationGraph> {
    public static final String VAE_NETWORK = "vaeNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "combined_similarity_vae_data");

    ComputationGraph vaeNetwork;


    public CombinedVariationalAutoencoder(CombinedSimilarityVAEPipelineManager pipelineManager, String modelName) {
        super(pipelineManager,ComputationGraph.class,modelName);
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        System.out.println("Build model....");
        int hiddenLayerSize = 48;
        int input1 = 32;
        int input2 = 32;
        int numHiddenLayers = 20;

        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        // build networks
        int i = 0;
        ComputationGraphConfiguration.GraphBuilder conf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.001)
                .activation(Activation.TANH)
                .graphBuilder()
                .addInputs("x")
                .setOutputs("y")
                .addLayer(String.valueOf(i), NNOptimizer.newDenseLayer(input1+input2,hiddenLayerSize).build(), "x")
                .addLayer(String.valueOf(i+1), NNOptimizer.newDenseLayer(input1+input2+hiddenLayerSize,hiddenLayerSize).build(), String.valueOf(i), "x");

        int increment = 1;

        i+=2;

        int t = i;
        //  hidden layers
        for(; i < t + numHiddenLayers*increment; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize);
            conf = conf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        }

        org.deeplearning4j.nn.conf.layers.Layer.Builder encoding = NNOptimizer.newDenseLayer(hiddenLayerSize+hiddenLayerSize,32);
        conf = conf.addLayer(String.valueOf(i),encoding.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        i++;
        org.deeplearning4j.nn.conf.layers.Layer.Builder decoding = NNOptimizer.newDenseLayer(32,hiddenLayerSize);
        conf = conf.addLayer(String.valueOf(i),decoding.build(), String.valueOf(i-increment));
        i++;

        t=i;

        //  hidden layers
        for(; i < t + numHiddenLayers*increment; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(i==t?(hiddenLayerSize+32):(hiddenLayerSize+hiddenLayerSize),hiddenLayerSize);
            conf = conf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        }

        // output layers
        OutputLayer.Builder outputLayer = NNOptimizer.newOutputLayer(hiddenLayerSize+hiddenLayerSize,input1+input2).lossFunction(lossFunction);

        conf = conf.addLayer("y",outputLayer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));

        vaeNetwork = new ComputationGraph(conf.build());
        vaeNetwork.init();

        //syncParams(wordCpc2Vec,cpcVecNet,encodingIdx);

        Map<String,ComputationGraph> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());
        nameToNetworkMap.put(VAE_NETWORK,vaeNetwork);
        return nameToNetworkMap;
    }

    @Override
    protected Map<String, ComputationGraph> updateNetworksBeforeTraining(Map<String, ComputationGraph> networkMap) {
        double newLearningRate = 0.0001;
        vaeNetwork = CGRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(VAE_NETWORK),newLearningRate,false);
        Map<String,ComputationGraph> updates = Collections.synchronizedMap(new HashMap<>());
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
