package learning;

import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;
import seeding.Database;

import java.io.File;


/**
 * Created by ehallmark on 7/21/16.
 */
public class SimilarityAutoEncoderModel extends AbstractPatentModel{
    public SimilarityAutoEncoderModel() throws Exception {
        super(100, 5, new File(Constants.SIMILARITY_MODEL_FILE));
    }

    @Override
    protected MultiLayerNetwork buildAndFitModel() {
        System.out.println("Load data....");
        DataSetIterator iter = new SimilarityAutoEncoderIterator(batchSize, oneDList, twoDList, true);
        DataSetIterator test = new SimilarityAutoEncoderIterator(batchSize, oneDList, twoDList, false);

        int vectorSize = iter.inputColumns();
        System.out.println("Number of vectors in input: "+vectorSize);

        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(41)
                .iterations(iterations)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(0, new RBM.Builder().nIn(vectorSize).nOut(1000).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(1, new RBM.Builder().nIn(1000).nOut(500).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(2, new RBM.Builder().nIn(500).nOut(250).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(3, new RBM.Builder().nIn(250).nOut(100).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(4, new RBM.Builder().nIn(100).nOut(30).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())

                //encoding stops
                .layer(5, new RBM.Builder().nIn(30).nOut(100).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())

                //decoding starts
                .layer(6, new RBM.Builder().nIn(100).nOut(250).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(7, new RBM.Builder().nIn(250).nOut(500).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(8, new RBM.Builder().nIn(500).nOut(1000).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(9, new OutputLayer.Builder(LossFunctions.LossFunction.RMSE_XENT).nIn(1000).nOut(vectorSize).build())
                .pretrain(true).backprop(true)
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();

        model.setListeners(new ScoreIterationListener(10));

        System.out.println("Train model....");
        while(iter.hasNext()) {
            DataSet next = iter.next();
            model.fit(new DataSet(next.getFeatureMatrix(), next.getFeatureMatrix()));
        }

        Evaluation evaluation = new Evaluation();
        while(test.hasNext()){
            DataSet t = test.next();
            INDArray features = t.getFeatureMatrix();
            System.out.println("Features: "+features.toString());
            INDArray predicted = model.output(features,false);
            System.out.println("Predicted: "+predicted.toString());
            evaluation.eval(features,predicted);
        }

        System.out.println(evaluation.stats());

        return model;
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            new SimilarityAutoEncoderModel();
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }
}
