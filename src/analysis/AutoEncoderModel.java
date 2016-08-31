package analysis;

import org.deeplearning4j.datasets.iterator.DataSetIterator;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
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
    protected int encodingSize;
    protected int iterations;
    protected DataSetIterator iter;
    protected DataSetIterator test;

    public AutoEncoderModel(DataSetIterator iter, DataSetIterator test, int batchSize, int iterations, int numEpochs, int encodingSize, File toSaveModel) throws Exception {
        this.batchSize=batchSize;
        this.iterations=iterations;
        this.iter = iter;
        this.encodingSize=encodingSize;
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
            while(iter.hasNext()) {
                model.fit(iter.next(batchSize));
            }
            iter.reset();

            // test
            List<Double> values = new ArrayList<>();
            while(test.hasNext()){
                DataSet t = test.next();
                for(int j = 0; j <  t.getFeatureMatrix().rows(); j++) {
                    INDArray row = t.getFeatureMatrix().getRow(j);
                    double similarity = Transforms.cosineSim(row, decode(encode(row)));
                    values.add(similarity);
                    System.out.println("Encoding: "+encode(row).toString());
                }

            }
            INDArray array = Nd4j.create(VectorHelper.toPrim(values.toArray(new Double[]{})));
            System.out.println(" --- AVERAGE SIMILARITY: "+array.meanNumber().toString());
            System.out.println(" --- VARIANCE OF SIMILARITY: "+array.varNumber().toString());
            test.reset();
        }
    }

    public INDArray encode(INDArray toEncode) {
        return model.activateSelectedLayers(0, 2, toEncode);
    }

    public INDArray decode(INDArray toDecode) {
        return model.activateSelectedLayers(3, 5, toDecode);
    }

    protected void saveModel(File toSave) throws IOException {
        ModelSerializer.writeModel(model, toSave, true);
    }

    protected MultiLayerNetwork buildModel() {
        int vectorSize = iter.inputColumns();
        int numHidden = 4000;
        System.out.println("Number of vectors in input: "+vectorSize);

        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(41)
                .iterations(iterations)
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                //.biasInit(-0.1)
                //.dropOut(0.2)
                .updater(Updater.ADAGRAD)
                .miniBatch(true)
                .activation("sigmoid")
                .gradientNormalization(GradientNormalization.ClipL2PerLayer)
                //.momentum(0.5)
                .learningRateDecayPolicy(LearningRatePolicy.Score)
                .lrPolicyDecayRate(0.001)
                //.l1(0.1).l2(0.001).regularization(true)
                .learningRate(0.0001)
                .list()
                .layer(0, new RBM.Builder().nIn(vectorSize).k(2).nOut(numHidden).hiddenUnit(RBM.HiddenUnit.RECTIFIED).visibleUnit(RBM.VisibleUnit.LINEAR).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(1, new RBM.Builder().nIn(numHidden).k(2).nOut(numHidden).hiddenUnit(RBM.HiddenUnit.RECTIFIED).visibleUnit(RBM.VisibleUnit.LINEAR).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(2, new RBM.Builder().nIn(numHidden).k(2).nOut(encodingSize).hiddenUnit(RBM.HiddenUnit.RECTIFIED).visibleUnit(RBM.VisibleUnit.LINEAR).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())

                //encoding stops
                .layer(3, new RBM.Builder().nIn(encodingSize).k(2).nOut(numHidden).lossFunction(LossFunctions.LossFunction.RMSE_XENT).hiddenUnit(RBM.HiddenUnit.BINARY).visibleUnit(RBM.VisibleUnit.BINARY).build())

                //decoding starts
                .layer(4, new RBM.Builder().nIn(numHidden).k(2).nOut(numHidden).hiddenUnit(RBM.HiddenUnit.RECTIFIED).visibleUnit(RBM.VisibleUnit.LINEAR).lossFunction(LossFunctions.LossFunction.RMSE_XENT).build())
                .layer(5, new OutputLayer.Builder(LossFunctions.LossFunction.RMSE_XENT).nIn(numHidden).nOut(vectorSize).build())
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

            int batchSize = 50;
            int iterations = 2;
            int encodingSize = 20;
            int numEpochs = 5;

            //SimilarPatentFinder finder1 = new SimilarPatentFinder(null, new File("candidateSets/598"),"ETSI");
            //SimilarPatentFinder finder2 = new SimilarPatentFinder(null, new File("candidateSets/596"),"Telia Custom");
            AutoEncoderModel model = new AutoEncoderModel(new BOWIterator(10000), new BOWIterator(batchSize), batchSize, iterations, numEpochs, encodingSize, new File(Constants.SIMILARITY_MODEL_FILE));

        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            Database.close();
        }
    }
}
