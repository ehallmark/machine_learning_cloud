package models.similarity_models.combined_similarity_model;

import data_pipeline.optimize.nn_optimization.NNOptimizer;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedSimilarityModel extends AbstractCombinedSimilarityModel<MultiLayerNetwork> {
    public static final String WORD_CPC_2_VEC = "wordCpc2Vec";
    public static final String CPC_VEC_NET = "cpcVecNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "combined_similarity_model_data");

    MultiLayerNetwork wordCpc2Vec;
    MultiLayerNetwork cpcVecNet;
    boolean useBatchNormalization = false;
    int hiddenLayerSize = 128;
    int input1 = 32;
    int input2 = 32;
    int outputSize = input1+input2;
    int numHiddenEncodings = 3;
    int numHiddenDecodings = 3;
    boolean trainWordCpc2Vec = true;
    boolean trainCpcVecNet = true;
    Updater updater = Updater.RMSPROP;

    public CombinedSimilarityModel(CombinedSimilarityPipelineManager pipelineManager, String modelName) {
        super(pipelineManager,MultiLayerNetwork.class,modelName);
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    protected Map<String, MultiLayerNetwork> buildNetworksForTraining() {
        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        // build networks
        int i = 0;
        NeuralNetConfiguration.ListBuilder wordCPC2VecConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.001)
                .activation(Activation.TANH)
                .list()
                .layer(i, NNOptimizer.newDenseLayer(input1,hiddenLayerSize).build());
        if(useBatchNormalization) wordCPC2VecConf = wordCPC2VecConf
                .layer(i+1, NNOptimizer.newBatchNormLayer(hiddenLayerSize,hiddenLayerSize).build());

        NeuralNetConfiguration.ListBuilder cpcVecNetConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.001)
                .activation(Activation.TANH)
                .list()
                .layer(i, NNOptimizer.newDenseLayer(input2,hiddenLayerSize).build());
        if(useBatchNormalization) cpcVecNetConf = cpcVecNetConf
                .layer(i+1, NNOptimizer.newBatchNormLayer(hiddenLayerSize,hiddenLayerSize).build());

        // encoding hidden layers
        int increment = 1 + (useBatchNormalization ? 1 : 0);

        i++;
        if(useBatchNormalization) i++;

        int t = i;
        // decoding hidden layers
        for(; i < t + (numHiddenEncodings + numHiddenDecodings)*increment; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize,hiddenLayerSize);
            wordCPC2VecConf = wordCPC2VecConf.layer(i,layer.build());
            cpcVecNetConf = cpcVecNetConf.layer(i,layer.build());
            if(useBatchNormalization) {
                org.deeplearning4j.nn.conf.layers.Layer.Builder norm = NNOptimizer.newBatchNormLayer(hiddenLayerSize,hiddenLayerSize);
                wordCPC2VecConf = wordCPC2VecConf.layer(i+1,norm.build());
                cpcVecNetConf = cpcVecNetConf.layer(i+1,norm.build());
            }
        }

        // output layers
        OutputLayer.Builder outputLayer = NNOptimizer.newOutputLayer(hiddenLayerSize,outputSize).lossFunction(lossFunction);

        wordCPC2VecConf = wordCPC2VecConf.layer(i,outputLayer.build());
        cpcVecNetConf = cpcVecNetConf.layer(i,outputLayer.build());

        wordCpc2Vec = new MultiLayerNetwork(wordCPC2VecConf.build());
        cpcVecNet = new MultiLayerNetwork(cpcVecNetConf.build());

        wordCpc2Vec.init();
        cpcVecNet.init();

        //syncParams(wordCpc2Vec,cpcVecNet,encodingIdx);

        Map<String,MultiLayerNetwork> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());
        nameToNetworkMap.put(WORD_CPC_2_VEC,wordCpc2Vec);
        nameToNetworkMap.put(CPC_VEC_NET,cpcVecNet);
        return nameToNetworkMap;
    }

    @Override
    protected Map<String, MultiLayerNetwork> updateNetworksBeforeTraining(Map<String, MultiLayerNetwork> networkMap) {
        double newLearningRate = 0.0001;
        double newRegularization = 1e-4;
        wordCpc2Vec = NNRefactorer.updateNetworkRegularization(NNRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(WORD_CPC_2_VEC),newLearningRate,false),newRegularization>0,newRegularization,false);
        cpcVecNet = NNRefactorer.updateNetworkRegularization(NNRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(CPC_VEC_NET),newLearningRate,false),newRegularization>0,newRegularization,false);
        Map<String,MultiLayerNetwork> updates = Collections.synchronizedMap(new HashMap<>());
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
