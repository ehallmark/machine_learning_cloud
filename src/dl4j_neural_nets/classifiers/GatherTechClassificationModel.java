package dl4j_neural_nets.classifiers;

import dl4j_neural_nets.iterators.datasets.ParagraphVectorDataSetIterator;
import dl4j_neural_nets.tests.ModelEvaluator;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Database;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 12/8/16.
 */
public class GatherTechClassificationModel {
    private static final File modelFile = new File("gather_technology_model.rbm");
    public static void main(String[] args) throws Exception {
        //Word2Vec wordVectors = WordVectorModel.load(WordVectorModel.oldWordVectorFile);
        WeightLookupTable<VocabWord> lookupTable = ParagraphVectorModel.loadModel(ParagraphVectorModel.claimsParagraphVectorFile.getAbsolutePath()+"11").getLookupTable();

        // get data
        Pair<Map<String,List<String>>,Map<String,List<String>>> data = Database.getGatherTechTestAndTrain();

        // set options
        int batchSize = 10;     //Number of examples in each minibatch
        int vectorSize = lookupTable.layerSize();   //Size of the word vectors. 300 in the Google News model
        int numOutputs = data.getFirst().size();
        int hiddenLayerSize = (vectorSize+numOutputs+1)/2;
        int nEpochs = 200;        //Number of epochs (full passes of training data) to train on

        List<String> labels = new ArrayList<>(data.getFirst().keySet());

        DataSetIterator trainIterator = new ParagraphVectorDataSetIterator(data.getFirst(),labels,batchSize,false,lookupTable);
        DataSetIterator testIterator = new ParagraphVectorDataSetIterator(data.getSecond(),labels,batchSize,false,lookupTable);

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(3)
                .updater(Updater.ADAGRAD)
                .weightInit(WeightInit.XAVIER)
                //.dropOut(0.6)
                //.miniBatch(batchSize > 1)
                .seed(41)
                .momentum(0.8)
                .learningRate(0.001)
                .list()
                .layer(0, new RBM.Builder().k(10).dropOut(-1).lossFunction(LossFunctions.LossFunction.MSE).activation("sigmoid").hiddenUnit(RBM.HiddenUnit.BINARY).visibleUnit(RBM.VisibleUnit.BINARY).nIn(vectorSize).nOut(hiddenLayerSize)
                        .build())
                .layer(1, new RBM.Builder().k(10).dropOut(0.6).lossFunction(LossFunctions.LossFunction.MSE).activation("sigmoid").hiddenUnit(RBM.HiddenUnit.BINARY).visibleUnit(RBM.VisibleUnit.BINARY).nIn(hiddenLayerSize).nOut(hiddenLayerSize)
                        .build())
                .layer(2, new OutputLayer.Builder().dropOut(0.5).activation("softmax").lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nIn(hiddenLayerSize).nOut(numOutputs).build())
                .pretrain(true).backprop(true).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        net.setListeners(new ScoreIterationListener(1000));

        System.out.println("Starting training");
        for( int i=0; i<nEpochs; i++ ){
            net.fit(trainIterator);
            trainIterator.reset();
            System.out.println("Epoch " + i + " complete. Starting evaluation:");

            // Evaluate model
            String stats = new ModelEvaluator().evaluateModel(testIterator,net);
            System.out.println(stats);
        }

        // Save model
        ModelSerializer.writeModel(net,modelFile,true);


        System.out.println("----- Example complete -----");
    }
}
