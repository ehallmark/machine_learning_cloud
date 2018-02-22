package models.similarity_models.combined_similarity_model;

import data_pipeline.optimize.nn_optimization.CGRefactorer;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import models.similarity_models.deep_cpc_encoding_model.DeepCPCVariationalAutoEncoderNN;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedSimilarityComputationGraph extends AbstractCombinedSimilarityModel<ComputationGraph,CombinedSimilarityPipelineManager> {
    public static final String WORD_CPC_2_VEC = "wordCpc2Vec";
    public static final String CPC_VEC_NET = "cpcVecNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"combined_similarity_graph_data");

    ComputationGraph wordCpc2Vec;
    ComputationGraph cpcVecNet;
    CombinedVariationalAutoencoder autoencoder;

    public CombinedSimilarityComputationGraph(CombinedSimilarityPipelineManager pipelineManager, String modelName, CombinedVariationalAutoencoder autoencoder) {
        super(pipelineManager,ComputationGraph.class,modelName);
        this.autoencoder=autoencoder;

    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        int hiddenLayerSize;
        int input1;
        int input2;
        int numHiddenLayers;

        if(CombinedSimilarityVAEPipelineManager.USE_DEEP_MODEL) {
            hiddenLayerSize = 128;
            input1 = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(WordCPC2VecPipelineManager.DEEP_MODEL_NAME);
            input2 = DeepCPCVariationalAutoEncoderNN.VECTOR_SIZE;
            numHiddenLayers = 32;
        } else {
            hiddenLayerSize = 48;
            input1 = 32;
            input2 = 32;
            numHiddenLayers = 32;
        }

        Updater updater = Updater.RMSPROP;

        LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

        // build networks
        int i = 0;
        ComputationGraphConfiguration.GraphBuilder wordCPC2VecConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.001)
                .activation(Activation.TANH)
                .graphBuilder()
                .addInputs("x","d")
                .setOutputs("y","d")
                .addLayer(String.valueOf(i), NNOptimizer.newDenseLayer(input1+1,hiddenLayerSize).build(), "x","d")
                .addLayer(String.valueOf(i+1), NNOptimizer.newDenseLayer(input1+hiddenLayerSize,hiddenLayerSize).build(), String.valueOf(i), "x");

        ComputationGraphConfiguration.GraphBuilder cpcVecNetConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                .updater(updater)
                .learningRate(0.001)
                .activation(Activation.TANH)
                .graphBuilder()
                .addInputs("x")
                .setOutputs("y","d")
                .addLayer(String.valueOf(i), NNOptimizer.newDenseLayer(input2+1,hiddenLayerSize).build(), "x", "d")
                .addLayer(String.valueOf(i+1), NNOptimizer.newDenseLayer(input2+hiddenLayerSize,hiddenLayerSize).build(), String.valueOf(i), "x");

        int increment = 1;

        i+=2;

        int t = i;
        //  hidden layers
        for(; i < t + (numHiddenLayers*increment)/2; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize);
            wordCPC2VecConf = wordCPC2VecConf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
            cpcVecNetConf = cpcVecNetConf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        }


        t = i;
        //  hidden layers
        for(; i < t + (numHiddenLayers*increment)/2; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize);
            wordCPC2VecConf = wordCPC2VecConf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
            cpcVecNetConf = cpcVecNetConf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        }

        // output layers
        OutputLayer.Builder outputLayer1 = NNOptimizer.newOutputLayer(hiddenLayerSize+hiddenLayerSize,autoencoder.getVectorSize()).lossFunction(lossFunction);
        OutputLayer.Builder outputLayer2 = NNOptimizer.newOutputLayer(hiddenLayerSize+hiddenLayerSize,autoencoder.getVectorSize()).lossFunction(lossFunction);
        OutputLayer.Builder dateLayer1 = NNOptimizer.newOutputLayer(hiddenLayerSize+hiddenLayerSize,1).lossFunction(LossFunctions.LossFunction.MSE);
        OutputLayer.Builder dateLayer2 = NNOptimizer.newOutputLayer(hiddenLayerSize+hiddenLayerSize,1).lossFunction(LossFunctions.LossFunction.MSE);

        wordCPC2VecConf = wordCPC2VecConf.addLayer("y",outputLayer1.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        cpcVecNetConf = cpcVecNetConf.addLayer("y",outputLayer2.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));

        wordCPC2VecConf = wordCPC2VecConf.addLayer("d",dateLayer1.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        cpcVecNetConf = cpcVecNetConf.addLayer("d",dateLayer2.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));

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
    protected Function<Object, Double> getTestFunction() {
        MultiDataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        List<Pair<MultiDataSet,MultiDataSet>> validationDataSets = Collections.synchronizedList(new ArrayList<>());
        int valCount = 0;
        while(validationIterator.hasNext()&&valCount<20000) {
            MultiDataSet dataSet = validationIterator.next();
            INDArray features = dataSet.getFeatures(0);
            INDArray labels = dataSet.getFeatures(1);
            INDArray dates = dataSet.getFeatures(2);
            INDArray encoding = autoencoder.encode(DEFAULT_LABEL_FUNCTION.apply(features,labels));
            MultiDataSet ds1 = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{features,dates},new INDArray[]{encoding,dates});
            MultiDataSet ds2 = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{labels,dates},new INDArray[]{encoding,dates});
            validationDataSets.add(new Pair<>(ds1,ds2));
            valCount+=ds1.getFeatures()[0].rows();
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


    public Pair<Double,Double> test(ComputationGraph net1, ComputationGraph net2, Iterator<Pair<MultiDataSet,MultiDataSet>> iterator) {
        double d1 = 0;
        double d2 = 0;
        long count = 0;
        while(iterator.hasNext()) {
            Pair<MultiDataSet,MultiDataSet> dataSets = iterator.next();

            Pair<Double,Double> test = new Pair<>(test(net1,dataSets.getFirst()),test(net2,dataSets.getSecond()));
            d1+=test.getFirst();
            d2+=test.getSecond();
            count++;
        }
        if(count>0) {
            d1/=count;
            d2/=count;
        }
        return new Pair<>(d1,d2);
    }

    @Override
    protected void train(MultiDataSet dataSet) {
        INDArray features = dataSet.getFeatures(0);
        INDArray labels = dataSet.getFeatures(1);
        INDArray dates = dataSet.getFeatures(2);
        INDArray encoding = autoencoder.encode(DEFAULT_LABEL_FUNCTION.apply(features,labels));
        MultiDataSet ds1 = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{features,dates},new INDArray[]{encoding,dates});
        MultiDataSet ds2 = new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{labels,dates},new INDArray[]{encoding,dates});
        train(wordCpc2Vec, ds1);
        train(cpcVecNet, ds2);
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
