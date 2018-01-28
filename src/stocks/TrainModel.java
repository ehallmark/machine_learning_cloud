package stocks;

import data_pipeline.optimize.nn_optimization.NNOptimizer;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.List;

/**
 * Created by ehallmark on 11/17/17.
 */
public class TrainModel {
    private static final File modelFile = new File(Constants.DATA_FOLDER+"stock_model_nn.jobj");
    public static void main(String[] args) throws Exception {
        final int nEpochs = 10;
        final int numInputs = BuildTrainableDataset.windowSizeMonthsBefore*13;
        final int numOutputs = BuildTrainableDataset.windowSizeMonthsAfter*1;
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(Updater.RMSPROP)
                .rmsDecay(0.95)
                .learningRate(0.0001)
                .activation(Activation.TANH)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(0, NNOptimizer.newDenseLayer(numInputs, 50).build())
                .layer(1, NNOptimizer.newBatchNormLayer(100,100).build())
                .layer(2, NNOptimizer.newDenseLayer(100,100).build())
                .layer(3, NNOptimizer.newBatchNormLayer(100,100).build())
                .layer(4, NNOptimizer.newOutputLayer(100,numOutputs).activation(Activation.IDENTITY).lossFunction(LossFunctions.LossFunction.MSE).build())
                .backprop(true)
                .pretrain(false)
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        List<DataSet> devData = (List<DataSet>) Database.tryLoadObject(BuildTrainableDataset.valFolder);

        net.setListeners(new ScoreIterationListener(5000) {
            @Override
            public void invoke() {
                super.invoke();
                // test
                System.out.println("Testing...");
                double error = devData.stream().mapToDouble(ds->{
                    INDArray prediction = net.activateSelectedLayers(0,net.getnLayers()-1,ds.getFeatures().reshape(1,numInputs));
                    return prediction.distance2(ds.getLabels().reshape(1,numInputs));
                }).average().orElse(Double.MAX_VALUE);
                System.out.println("Average error: "+error);
            }
        });

        System.out.println("Loading data...");
        List<DataSet> trainData = (List<DataSet>) Database.tryLoadObject(BuildTrainableDataset.trainFolder);
        System.out.println("Loaded.");
        for(int i = 0; i < nEpochs; i++) {
            trainData.forEach(ds -> {
                ds.setFeatures(ds.getFeatures().reshape(1,numInputs));
                ds.setLabels(ds.getLabels().reshape(1,numInputs));
                net.fit(ds);
            });
        }

        System.out.println("Writing model to disk...");
        ModelSerializer.writeModel(net,modelFile,true);
    }
}
