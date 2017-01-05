package dl4j_neural_nets.classifiers;

import dl4j_neural_nets.iterators.datasets.ParagraphVectorDataSetIterator;
import dl4j_neural_nets.tests.ModelEvaluator;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.apache.commons.io.FileUtils;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/8/16.
 */
public class GatherTransactionProbabilityModel {
    private static final File modelFile = new File("transaction_probability_model.nn");
    public static void main(String[] args) throws Exception {
        //WeightLookupTable<VocabWord> lookupTable = WordVectorSerializer.loadGoogleModel(new File(Constants.GOOGLE_WORD_VECTORS_PATH),true).lookupTable();
        WeightLookupTable<VocabWord> lookupTable = ParagraphVectorModel.loadAllSentencesModel().getLookupTable();

        // get data
        Map<String,List<String>> trainData = new HashMap<>();
        Map<String,List<String>> testData = new HashMap<>();

        List<String> valuables = FileUtils.readLines(new File("valuable_patents.csv")).stream()
                .filter(p->lookupTable.vector(p)!=null).collect(Collectors.toList());
        List<String> unvaluables = FileUtils.readLines(new File("unvaluable_patents.csv")).stream()
                .filter(p->lookupTable.vector(p)!=null).collect(Collectors.toList());

        System.out.println("Num valuable patents found in lookuptable: "+valuables.size());
        System.out.println("Num unvaluable patents found in lookuptable: "+unvaluables.size());

        Collections.shuffle(valuables);
        Collections.shuffle(unvaluables);

        int numTestOnes = Math.round(0.75f*valuables.size());
        int numTestZeroes = Math.round(0.75f*unvaluables.size());
        trainData.put("0",unvaluables.subList(0,numTestZeroes));
        trainData.put("1",valuables.subList(0,numTestOnes));
        testData.put("0",unvaluables.subList(numTestZeroes,unvaluables.size()));
        testData.put("1",valuables.subList(numTestOnes,valuables.size()));

        // set options
        int batchSize = 6;     //Number of examples in each minibatch
        int vectorSize = lookupTable.layerSize();//+lookupTable2.layerSize();   //Size of the word vectors. 300 in the Google News model
        int numOutputs = 2;
        int hiddenLayerSize = 300; // Lots of brain power!
        int nEpochs = 20;        //Number of epochs (full passes of training data) to train on

        List<String> labels = Arrays.asList("0","1");

        DataSetIterator trainIterator = new ParagraphVectorDataSetIterator(trainData,labels,batchSize,false,lookupTable);//,lookupTable2);
        DataSetIterator testIterator = new ParagraphVectorDataSetIterator(testData,labels,batchSize,false,lookupTable);//,lookupTable2);

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(Updater.ADAGRAD)
                .weightInit(WeightInit.XAVIER)
                //.momentum(0.7)
                .seed(41)
                .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                .gradientNormalizationThreshold(1.0)
                .l2(0.0001).regularization(true)
                .learningRate(0.01)
                .list()
                .layer(0, new DenseLayer.Builder().activation("sigmoid").nIn(vectorSize).nOut(hiddenLayerSize).build())
                .layer(1, new DenseLayer.Builder().activation("sigmoid").nIn(hiddenLayerSize).nOut(hiddenLayerSize).build())
                .layer(2, new OutputLayer.Builder().activation("softmax").lossFunction(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD).nIn(hiddenLayerSize).nOut(numOutputs).build())
                .pretrain(false).backprop(true).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        net.setListeners(new ScoreIterationListener(1000));

        System.out.println("Starting training");
        double maxScore = 0d;
        int bestEpoch = -1;
        for( int i=0; i<nEpochs; i++ ){
            net.fit(trainIterator);
            trainIterator.reset();
            System.out.println("Epoch " + i + " complete. Starting evaluation:");

            // Evaluate model
            String stats = new ModelEvaluator().evaluateModel(testIterator,net);
            System.out.println(stats);
        }

        System.out.println("BEST EPOCH: "+bestEpoch);

        // Save model
        ModelSerializer.writeModel(net,modelFile,true);


        System.out.println("----- Example complete -----");
    }

    public static MultiLayerNetwork load() {
        try {
            return ModelSerializer.restoreMultiLayerNetwork(modelFile);
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
