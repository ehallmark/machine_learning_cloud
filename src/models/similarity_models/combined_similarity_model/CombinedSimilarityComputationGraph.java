package models.similarity_models.combined_similarity_model;

import data_pipeline.helpers.Function2;
import data_pipeline.optimize.nn_optimization.CGRefactorer;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedSimilarityComputationGraph extends AbstractCombinedSimilarityModel<ComputationGraph> {
    public static final String WORD_CPC_2_VEC = "wordCpc2Vec";
    public static final String CPC_VEC_NET = "cpcVecNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "combined_similarity_graph_data");

    boolean trainWordCpc2Vec = true;
    boolean trainCpcVecNet = true;
    ComputationGraph wordCpc2Vec;
    ComputationGraph cpcVecNet;

    public CombinedSimilarityComputationGraph(CombinedSimilarityPipelineManager pipelineManager, String modelName) {
        super(pipelineManager,ComputationGraph.class,modelName);
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        int hiddenLayerSize = 64;
        int input1 = 32;
        int input2 = 32;
        int numHiddenLayers = 8;

        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        // build networks
        int i = 0;
        ComputationGraphConfiguration.GraphBuilder wordCPC2VecConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.001)
                .activation(Activation.TANH)
                .graphBuilder()
                .addInputs("x")
                .setOutputs("y1","y2")
                .addLayer(String.valueOf(i), NNOptimizer.newDenseLayer(input1,hiddenLayerSize).build(), "x")
                .addLayer(String.valueOf(i+1), NNOptimizer.newDenseLayer(input1+hiddenLayerSize,hiddenLayerSize).build(), String.valueOf(i), "x");

        ComputationGraphConfiguration.GraphBuilder cpcVecNetConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.001)
                .activation(Activation.TANH)
                .graphBuilder()
                .addInputs("x")
                .setOutputs("y1","y2")
                .addLayer(String.valueOf(i), NNOptimizer.newDenseLayer(input2,hiddenLayerSize).build(), "x")
                .addLayer(String.valueOf(i+1), NNOptimizer.newDenseLayer(input2+hiddenLayerSize,hiddenLayerSize).build(), String.valueOf(i), "x");

        int increment = 1;

        i+=2;

        int t = i;
        //  hidden layers
        for(; i < t + numHiddenLayers*increment; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize);
            wordCPC2VecConf = wordCPC2VecConf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-(2*increment)));
            cpcVecNetConf = cpcVecNetConf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-(2*increment)));
        }

        // output layers
        OutputLayer.Builder outputLayer1 = NNOptimizer.newOutputLayer(hiddenLayerSize+hiddenLayerSize,input1).lossFunction(lossFunction);
        OutputLayer.Builder outputLayer2 = NNOptimizer.newOutputLayer(hiddenLayerSize+hiddenLayerSize,input2).lossFunction(lossFunction);

        wordCPC2VecConf = wordCPC2VecConf.addLayer("y1",outputLayer1.build(), String.valueOf(i-increment), String.valueOf(i-(2*increment)));
        cpcVecNetConf = cpcVecNetConf.addLayer("y1",outputLayer1.build(), String.valueOf(i-increment), String.valueOf(i-(2*increment)));

        wordCPC2VecConf = wordCPC2VecConf.addLayer("y2",outputLayer2.build(), String.valueOf(i-increment), String.valueOf(i-(2*increment)));
        cpcVecNetConf = cpcVecNetConf.addLayer("y2",outputLayer2.build(), String.valueOf(i-increment), String.valueOf(i-(2*increment)));

        wordCpc2Vec = new ComputationGraph(wordCPC2VecConf.build());
        cpcVecNet = new ComputationGraph(cpcVecNetConf.build());

        wordCpc2Vec.init();
        cpcVecNet.init();

        //syncParams(wordCpc2Vec,cpcVecNet,encodingIdx);

        Map<String,ComputationGraph> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());
        nameToNetworkMap.put(WORD_CPC_2_VEC,wordCpc2Vec);
        nameToNetworkMap.put(CPC_VEC_NET,cpcVecNet);
        return nameToNetworkMap;
    }

    @Override
    protected Map<String, ComputationGraph> updateNetworksBeforeTraining(Map<String, ComputationGraph> networkMap) {
        double newLearningRate = 0.0001;
        wordCpc2Vec = CGRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(WORD_CPC_2_VEC),newLearningRate,false);
        cpcVecNet = CGRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(CPC_VEC_NET),newLearningRate,false);
        Map<String,ComputationGraph> updates = Collections.synchronizedMap(new HashMap<>());
        updates.put(WORD_CPC_2_VEC,wordCpc2Vec);
        updates.put(CPC_VEC_NET,cpcVecNet);
        return updates;
    }

    @Override
    protected Function<Void, Double> getTestFunction() {
        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        List<DataSet> validationDataSets = Collections.synchronizedList(new ArrayList<>());
        int valCount = 0;
        while(validationIterator.hasNext()&&valCount<20000) {
            DataSet ds = validationIterator.next();
            validationDataSets.add(ds);
            valCount+=ds.getFeatures().rows();
            //System.gc();
        }

        System.out.println("Num validation datasets: "+validationDataSets.size());

        return (v) -> {
            System.gc();
            Pair<Double,Double> results = test(wordCpc2Vec, cpcVecNet, validationDataSets.iterator());
            System.out.println(" Test Net 1: "+results.getFirst()+"\tTest Net 2: "+results.getSecond());
            return (results.getFirst()+results.getSecond())/2;
        };
    }

    @Override
    protected void train(INDArray features, INDArray labels) {
        train(wordCpc2Vec, cpcVecNet, features, labels,trainWordCpc2Vec,trainCpcVecNet);
    }

    @Override
    protected Function<IterationListener, Void> setListenerFunction() {
        return listener -> {
            wordCpc2Vec.setListeners(listener);
            return null;
        };
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
