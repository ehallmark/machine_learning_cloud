package test;

import org.deeplearning4j.nn.conf.LearningRatePolicy;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;

import java.util.HashMap;
import java.util.Map;

public class TestNNIterationsLRSchedule {
    public static void main(String[] args) {
        Map<Integer,Double> lrMap = new HashMap<>();
        lrMap.put(0,0.1);
        lrMap.put(10,-1000d);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .learningRate(0.001)
                .learningRateSchedule(lrMap)
                .learningRateDecayPolicy(LearningRatePolicy.Schedule)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(2).nOut(2).build())
                .layer(1, new OutputLayer.Builder().nIn(2).nOut(2).build())
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);

        DataSet dataSet = new DataSet(Nd4j.rand(10,2),Nd4j.randn(new int[]{10,2}));

        for(int i = 0; i < 100; i++) {
            net.fit(dataSet);
            System.out.println("Fit "+i+": Score: "+net.score());
        }
    }
}
