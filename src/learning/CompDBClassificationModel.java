package learning;

import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;
import seeding.Database;

/**
 * Created by ehallmark on 7/19/16.
 */
public class CompDBClassificationModel {
    private MultiLayerNetwork model;
    private int numClassifications;

    public CompDBClassificationModel() throws Exception {
        this.numClassifications = Database.getNumberOfCompDBClassifications();
        System.out.println("Num classifications: "+numClassifications);
        int numHiddenNodesL1 = 750;
        int numHiddenNodesL2 = 750;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(41)
                .iterations(1)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.001)
                .momentum(0.7)
                .updater(Updater.NESTEROVS)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(Constants.VECTOR_LENGTH).nOut(numHiddenNodesL1)
                        .weightInit(WeightInit.XAVIER)
                        .activation("sigmoid")
                        .build())
                .layer(1, new DenseLayer.Builder().nIn(numHiddenNodesL1).nOut(numHiddenNodesL2)
                        .weightInit(WeightInit.XAVIER)
                        .activation("sigmoid")
                        .build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .weightInit(WeightInit.XAVIER)
                        .activation("softmax")
                        .nIn(numHiddenNodesL2).nOut(numClassifications).build())
                .pretrain(false).backprop(true).build();

        model = new MultiLayerNetwork(conf);
        model.setListeners(new ScoreIterationListener(1));
    }


    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            Database.setupCompDBConn();
            new CompDBClassificationModel();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }
}
