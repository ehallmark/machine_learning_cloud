package stocks;

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
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .learningRate(0.001)
                .regularization(true).l2(1e-4)
                .updater(Updater.ADAM)
                .activation(Activation.TANH)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(33).nOut(30).build())
                .layer(1, new BatchNormalization.Builder().nIn(30).nOut(30).minibatch(true).build())
                .layer(2, new DenseLayer.Builder().nIn(30).nOut(30).build())
                .layer(3, new BatchNormalization.Builder().nIn(30).nOut(30).minibatch(true).build())
                .layer(4, new OutputLayer.Builder(LossFunctions.LossFunction.MSE).nIn(10).nOut(1).activation(Activation.TANH).build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        List<DataSet> devData = (List<DataSet>) Database.tryLoadObject(BuildTrainableDataset.valFolder);

        net.setListeners(new ScoreIterationListener(10000) {
            @Override
            public void invoke() {
                super.invoke();
                // test
                System.out.println("Testing...");
                double error = devData.stream().mapToDouble(ds->{
                    INDArray prediction = net.activateSelectedLayers(0,net.getnLayers()-1,ds.getFeatures());
                    return prediction.distance2(ds.getLabels());
                }).average().orElse(Double.MAX_VALUE);
                System.out.println("Average error: "+error);
            }
        });

        System.out.println("Loading data...");
        List<DataSet> trainData = (List<DataSet>) Database.tryLoadObject(BuildTrainableDataset.trainFolder);
        System.out.println("Loaded.");
        trainData.forEach(ds->{
            net.fit(ds);
        });

        System.out.println("Writing model to disk...");
        ModelSerializer.writeModel(net,modelFile,true);
    }
}
