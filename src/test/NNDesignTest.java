package test;

import data_pipeline.helpers.Function2;
import models.similarity_models.combined_similarity_model.CombinedSimilarityModel;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Created by Evan on 12/24/2017.
 */
public class NNDesignTest {
    public static void main(String[] args) {
        int hiddenLayerSize = 10;
        int inputSize1 = 10;
        int inputSize2 = 5;
        int batchSize = 10;
        int syncPeriod = 10;
        int outputSize = inputSize1 + inputSize2;
        MultiLayerConfiguration nn1 = new NeuralNetConfiguration.Builder()
                .learningRate(0.1)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.NESTEROVS)
                .activation(Activation.TANH)
                .momentum(0.9)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(inputSize1).nOut(hiddenLayerSize).build())
                .layer(1, new DenseLayer.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build())
                .layer(2, new OutputLayer.Builder().nIn(hiddenLayerSize).nOut(outputSize).lossFunction(LossFunctions.LossFunction.COSINE_PROXIMITY).build())
                .build();

        MultiLayerConfiguration nn2 = new NeuralNetConfiguration.Builder()
                .learningRate(0.1)
                .weightInit(WeightInit.RELU)
                .activation(Activation.RELU)
                .updater(Updater.ADAGRAD)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(inputSize2).nOut(hiddenLayerSize).build())
                .layer(1, new DenseLayer.Builder().nIn(hiddenLayerSize).nOut(hiddenLayerSize).build())
                .layer(2, new OutputLayer.Builder().nIn(hiddenLayerSize).nOut(outputSize).lossFunction(LossFunctions.LossFunction.COSINE_PROXIMITY).build())
                .build();


        MultiLayerNetwork net1 = new MultiLayerNetwork(nn1);
        MultiLayerNetwork net2 = new MultiLayerNetwork(nn2);

        net1.init();
        net2.init();

        System.out.println("Params for net 1 before param sharing: "+net1.params().toString());
        System.out.println("Params for net 2 before param sharing: "+net2.params().toString());

        // similarity of output layer
        for(int i = 1; i < 3; i++ ) {
            System.out.println("Similarity of layer "+i+": "+ Transforms.cosineSim(net1.getLayer(i).params(),net2.getLayer(i).params()));
        }

        CombinedSimilarityModel.syncParams(net1,net2, 1);

        System.out.println("Params for net 1 after: "+net1.params().toString());
        System.out.println("Params for net 2 after: "+net2.params().toString());

        Function2<INDArray, INDArray, INDArray> featuresToLabelFunction = (v1,v2) -> Nd4j.hstack(v1,v2);

        System.out.println("Params for net 1 before training: "+net1.params().toString());
        System.out.println("Params for net 2 before training: "+net2.params().toString());

        // similarity of output layer
        for(int i = 1; i < 3; i++ ) {
            System.out.println("Similarity of layer "+i+": "+ Transforms.cosineSim(net1.getLayer(i).params(),net2.getLayer(i).params()));
        }

        for(int j = 0; j < 100000; j++) {
            train(net1, net2, Nd4j.randn(new int[]{batchSize,inputSize1}), Nd4j.rand(new int[]{batchSize,inputSize2}), featuresToLabelFunction, false);
            if(j%1000==999) {
                System.out.println("Iteration: "+j);
                // similarity of output layer
                for(int i = 1; i < 3; i++ ) {
                    System.out.println("Similarity of layer "+i+": "+ Transforms.cosineSim(net1.getLayer(i).params(),net2.getLayer(i).params()));
                }
                System.out.println("Score 1: "+net1.score());
                System.out.println("Score 2: "+net2.score());
            }
        }

        System.out.println("Params for net 1: "+net1.params().toString());
        System.out.println("Params for net 2: "+net2.params().toString());

        // similarity of output layer
        for(int i = 1; i < 3; i++ ) {
            System.out.println("Similarity of layer "+i+": "+ Transforms.cosineSim(net1.getLayer(i).params(),net2.getLayer(i).params()));
            System.out.println("Score 1: "+net1.score());
            System.out.println("Score 2: "+net2.score());
        }
    }

    public static void train(MultiLayerNetwork net1, MultiLayerNetwork net2, INDArray features1, INDArray features2, Function2<INDArray, INDArray, INDArray> featuresToLabelFunction, boolean syncParams) {
        INDArray labels = featuresToLabelFunction.apply(features1,features2);
        net1.fit(new DataSet(features1,labels));
        net2.fit(new DataSet(features2,labels));

        if(syncParams) {
            CombinedSimilarityModel.syncParams(net1,net2, 1);
        }
    }


}
