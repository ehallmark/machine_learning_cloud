package analysis;

import learning.AbstractPatentModel;
import learning.SimilarityAutoEncoderIterator;
import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.LearningRatePolicy;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import tools.VectorHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by ehallmark on 7/21/16.
 */
public class AutoEncoderModel {
    protected MultiLayerNetwork model;
    protected int batchSize;
    protected int iterations;
    protected DataSetIterator iter;
    protected DataSetIterator test;

    public AutoEncoderModel(DataSetIterator iter, DataSetIterator test, int batchSize, int iterations, int numEpochs, File toSaveModel) throws Exception {
        this.batchSize=batchSize;
        this.iterations=iterations;
        this.iter = iter;
        this.test = test;
        model=buildModel();
        model.setListeners(new ScoreIterationListener(1));
        fitModel(numEpochs);
        saveModel(toSaveModel);
    }

    protected void fitModel(int numEpochs) {
        System.out.println("Train model...");
        for(int i = 0; i < numEpochs; i++) {
            System.out.println("Epoch # "+(i+1));
            model.fit(iter);
            iter.reset();

            // test
            List<Double> values = new ArrayList<>();
            while(test.hasNext()){
                DataSet t = test.next();
                INDArray predicted = model.activateSelectedLayers(0, 5, t.getFeatureMatrix());
                for(int j = 0; j < predicted.rows(); j++) {
                    double similarity = Transforms.cosineSim(t.getFeatureMatrix().getRow(j), predicted.getRow(j));
                    //System.out.println("Cosine of angle between original and predicted: "+similarity);
                    values.add(similarity);
                }

            }
            INDArray array = Nd4j.create(VectorHelper.toPrim(values.toArray(new Double[]{})));
            System.out.println(" --- AVERAGE: "+array.meanNumber().toString());
            System.out.println(" --- VARIANCE: "+array.varNumber().toString());
            test.reset();
        }
    }

    public INDArray encode(INDArray toEncode) {
        return model.activateSelectedLayers(0, 2, toEncode);
    }

    protected void saveModel(File toSave) throws IOException {
        ModelSerializer.writeModel(model, toSave, true);
    }

    protected MultiLayerNetwork buildModel() {
        int vectorSize = iter.inputColumns();
        System.out.println("Number of vectors in input: "+vectorSize);

        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(41)
                .iterations(iterations)
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .dropOut(0.2)
                .updater(Updater.NESTEROVS)
                .momentum(0.7)
                .learningRateDecayPolicy(LearningRatePolicy.Score)
                .lrPolicyDecayRate(0.001)
                .learningRate(0.05)
                .list()
                .layer(0, new RBM.Builder().nIn(vectorSize).nOut(250).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(1, new RBM.Builder().nIn(250).nOut(100).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(2, new RBM.Builder().nIn(100).nOut(30).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())

                //encoding stops
                .layer(3, new RBM.Builder().nIn(30).nOut(100).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())

                //decoding starts
                .layer(4, new RBM.Builder().nIn(100).nOut(250).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(5, new OutputLayer.Builder(LossFunctions.LossFunction.RMSE_XENT).nIn(250).nOut(vectorSize).build())
                .pretrain(true).backprop(true)
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        return model;
    }

    public MultiLayerNetwork getTrainedNetwork() {
        return model;
    }

    public static void main(String[] args) {
        try {
            Database.setupSeedConn();
            System.out.println("Load data....");

            int batchSize = 100;
            int iterations = 10;
            int numEpochs = 5;

            SimilarPatentFinder finder1 = new SimilarPatentFinder(null, new File("candidateSets/6"));
            SimilarPatentFinder finder2 = new SimilarPatentFinder(null, new File("candidateSets/1"));
            AutoEncoderModel model = new AutoEncoderModel(new AutoEncoderIterator(batchSize, finder1), new AutoEncoderIterator(batchSize, finder2), batchSize, iterations, numEpochs, new File(Constants.SIMILARITY_MODEL_FILE));
            System.out.println(model.encode(finder2.getPatentList().get(0).getVector()));
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }
}
