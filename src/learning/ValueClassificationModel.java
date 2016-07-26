package learning;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.distribution.UniformDistribution;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;
import seeding.Database;

import java.io.File;

/**
 * Created by ehallmark on 7/19/16.
 */
public class ValueClassificationModel extends AbstractPatentModel {

    public ValueClassificationModel(ValueClassificationIterator iter, ValueClassificationIterator test, int batchSize, int iterations, int numEpochs) throws Exception {
        super(iter, test, batchSize, iterations, numEpochs, new File(Constants.VALUABLE_PATENT_MODEL_FILE));
    }

    @Override
    protected MultiLayerNetwork buildModel() {
        int numHiddenNodes = 750;
        int numHiddenNodes2 = 750;

        int vectorLength = iter.inputColumns();
        int numOutcomes = 1;

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(41)
                .iterations(iterations)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.001)
                .momentum(0.7)
                .updater(Updater.NESTEROVS)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(vectorLength).nOut(numHiddenNodes)
                        .weightInit(WeightInit.XAVIER)
                        .activation("relu")
                        .build())
                .layer(1, new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes2)
                        .weightInit(WeightInit.XAVIER)
                        .activation("relu")
                        .build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.RMSE_XENT)
                        .weightInit(WeightInit.DISTRIBUTION)
                        .dist(new UniformDistribution(0, 1))
                        .activation("sigmoid")
                        .nIn(numHiddenNodes2).nOut(numOutcomes).build())
                .pretrain(false).backprop(true).build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        return model;
    }


    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            System.out.println("Load data....");

            int batchSize = 100;
            int iterations = 3;
            int numEpochs = 1;

            ValueClassificationIterator iter = new ValueClassificationIterator(batchSize, Constants.DEFAULT_1D_VECTORS, Constants.DEFAULT_2D_VECTORS, true);
            ValueClassificationIterator test = new ValueClassificationIterator(batchSize, Constants.DEFAULT_1D_VECTORS, Constants.DEFAULT_2D_VECTORS, false);

            new ValueClassificationModel(iter, test, batchSize, iterations, numEpochs);
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }
}
