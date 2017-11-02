package models.dl4j_neural_nets.recurrent;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Database;
import org.deeplearning4j.berkeley.Pair;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/2/17.
 */
public class CPCTitleModel {
    static Collection<CPC> mainGroups;
    static Map<String,String> cpcToTitleMap;
    static Map<String,Integer> cpcToIdxMap;;
    public static void main(String[] args) {
        final int k = 10;
        final int batchSize = 30;

        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();

        cpcToTitleMap = Database.getClassCodeToClassTitleMap();

        mainGroups = hierarchy.getLabelToCPCMap().entrySet().parallelStream()
                .filter(e->e.getValue().getNumParts()==3)
                .map(e->e.getValue()).collect(Collectors.toList());

        System.out.println("Num main groups: "+mainGroups.size());

        mainGroups = mainGroups.parallelStream().filter(cpc->cpcToTitleMap.containsKey(cpc.getName()))
                .collect(Collectors.toList());

        System.out.println("Num main groups with valid title: "+mainGroups.size());
        {
            AtomicInteger idx = new AtomicInteger(0);
            cpcToIdxMap = mainGroups.parallelStream().collect(Collectors.toMap(cpc -> cpc.getName(), cpc -> idx.getAndIncrement()));
        }
        final int numCPCs = mainGroups.size();

        CharacterNGramIterator iterator = getIterator(numCPCs,k,batchSize);

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(69)
                .iterations(1) // Training iterations as above
                .regularization(true).l2(0.0005)
                .learningRate(.01)//.biasLearningRate(0.02)
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(Updater.NESTEROVS)
                .momentum(0.9)
                .list()
                .layer(0, new ConvolutionLayer.Builder(5, 5)
                        //nIn and nOut specify depth. nIn here is the nChannels and nOut is the number of filters to be applied
                        .nIn(1)
                        .stride(1, 1)
                        .nOut(20)
                        .activation(Activation.IDENTITY)
                        .build())
                .layer(1, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                        .kernelSize(2,2)
                        .stride(2,2)
                        .build())
                .layer(2, new ConvolutionLayer.Builder(5, 5)
                        //Note that nIn need not be specified in later layers
                        .stride(1, 1)
                        .nOut(50)
                        .activation(Activation.IDENTITY)
                        .build())
                .layer(3, new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                        .kernelSize(2,2)
                        .stride(2,2)
                        .build())
                .layer(4, new DenseLayer.Builder().activation(Activation.RELU)
                        .nOut(500).build())
                .layer(5, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .nOut(numCPCs)
                        .activation(Activation.SOFTMAX)
                        .build())
                .setInputType(InputType.convolutionalFlat(batchSize,iterator.inputColumns(),1)) //See note below
                .backprop(true).pretrain(false).build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new ScoreIterationListener(1000));


        int numEpochs = 10;
        for(int i = 0; i < numEpochs; i++) {
            System.out.println("Starting epoch: "+(i+1));
            net.fit(iterator);
            System.out.println("Score: "+net.score());

            Evaluation evaluation = new Evaluation(numCPCs);
            DataSetIterator test = getIterator(numCPCs,k,batchSize);
            while(test.hasNext()) {
                DataSet ds = test.next();
                INDArray predicted = net.output(ds.getFeatures(),false);
                //System.out.println("Prediction: "+predicted.toString());
                evaluation.eval(ds.getLabels(),predicted);
            }
            System.out.println(evaluation.stats());
            iterator = getIterator(numCPCs,k,batchSize);
        }
    }

    private static CharacterNGramIterator getIterator(int numCPCs, int k, int batchSize) {
        Iterator<Pair<String,INDArray>> textAndLabelIterator = mainGroups.parallelStream().map(cpc->{
            INDArray vec = Nd4j.zeros(numCPCs);
            vec.putScalar(cpcToIdxMap.get(cpc.getName()),1);
            return new Pair<>(cpcToTitleMap.get(cpc.getName()),vec);
        }).iterator();

        CharacterNGramIterator iterator = new CharacterNGramIterator(k,textAndLabelIterator,batchSize);
        return iterator;
    }
}
