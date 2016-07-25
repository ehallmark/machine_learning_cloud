package learning;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;
import seeding.Database;

import java.io.File;

/**
 * Created by ehallmark on 7/19/16.
 */
public class CompDBClassificationModel extends AbstractPatentModel {

    public CompDBClassificationModel() throws Exception {
        super(100, 5, new File(Constants.COMPDB_TECHNOLOGY_MODEL_FILE));
    }

    @Override
    protected MultiLayerNetwork buildAndFitModel() {
        int numHiddenNodesL1 = 750;
        int numHiddenNodesL2 = 750;
        CompDBClassificationIterator iter = new CompDBClassificationIterator(batchSize, oneDList, twoDList, true);
        //CompDBClassificationIterator test = new CompDBClassificationIterator(batchSize, oneDList, twoDList, false);

        int vectorLength = iter.inputColumns();
        int numOutcomes = iter.totalOutcomes();

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(41)
                .iterations(1)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(0.001)
                .momentum(0.7)
                .updater(Updater.NESTEROVS)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(vectorLength).nOut(numHiddenNodesL1)
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
                        .nIn(numHiddenNodesL2).nOut(numOutcomes).build())
                .pretrain(false).backprop(true).build();

        model = new MultiLayerNetwork(conf);
        model.setListeners(new ScoreIterationListener(1));

        while(iter.hasNext()) {
            DataSet data = iter.next();
            model.fit(data.getFeatureMatrix(), data.getLabels());
        }

        return model;
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
