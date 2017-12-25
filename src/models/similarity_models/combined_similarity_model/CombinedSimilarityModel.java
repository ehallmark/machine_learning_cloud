package models.similarity_models.combined_similarity_model;

import data_pipeline.helpers.CombinedModel;
import data_pipeline.helpers.Function2;
import data_pipeline.models.CombinedNeuralNetworkPredictionModel;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import models.NDArrayHelper;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedSimilarityModel extends CombinedNeuralNetworkPredictionModel<INDArray> {
    public static final String WORD_CPC_2_VEC = "wordCpc2Vec";
    public static final String CPC_VEC_NET = "cpcVecNet";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "combined_similarity_model_data");
    public static final Function2<INDArray,INDArray,INDArray> DEFAULT_LABEL_FUNCTION = (f1,f2) -> Nd4j.hstack(f1,f2);

    private CombinedSimilarityPipelineManager pipelineManager;
    public CombinedSimilarityModel(CombinedSimilarityPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    public void train(int nEpochs) {
        MultiLayerNetwork wordCpc2Vec;
        MultiLayerNetwork cpcVecNet;
        if(net==null) {
            int hiddenLayerSize = 32;
            int input1 = 128;
            int input2 = 32;
            int outputSize = input1+input2;
            int numLayers = 3;
            int syncLastNLayers = 2;
            LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

            // build networks
            NeuralNetConfiguration.ListBuilder wordCPC2VecConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                    .list()
                    .layer(0, NNOptimizer.newDenseLayer(input1,hiddenLayerSize).build());

            NeuralNetConfiguration.ListBuilder cpcVecNetConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                    .list()
                    .layer(0, NNOptimizer.newDenseLayer(input2,hiddenLayerSize).build());

            // hidden layers
            for(int i = 1; i < numLayers-1; i++) {
                org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize,hiddenLayerSize);
                wordCPC2VecConf = wordCPC2VecConf.layer(i,layer.build());
                cpcVecNetConf = cpcVecNetConf.layer(i,layer.build());
            }

            // output layers
            OutputLayer.Builder outputLayer = NNOptimizer.newOutputLayer(hiddenLayerSize,outputSize).lossFunction(lossFunction);

            wordCPC2VecConf = wordCPC2VecConf.layer(numLayers-1,outputLayer.build());
            cpcVecNetConf = cpcVecNetConf.layer(numLayers-1,outputLayer.build());

            wordCpc2Vec = new MultiLayerNetwork(wordCPC2VecConf.build());
            cpcVecNet = new MultiLayerNetwork(cpcVecNetConf.build());

            wordCpc2Vec.init();
            cpcVecNet.init();

            syncParams(wordCpc2Vec,cpcVecNet,syncLastNLayers);

            Map<String,MultiLayerNetwork> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());
            nameToNetworkMap.put(WORD_CPC_2_VEC,wordCpc2Vec);
            nameToNetworkMap.put(CPC_VEC_NET,cpcVecNet);
            this.net = new CombinedModel(nameToNetworkMap);
        } else {
            wordCpc2Vec = net.getNameToNetworkMap().get(WORD_CPC_2_VEC);
            cpcVecNet = net.getNameToNetworkMap().get(CPC_VEC_NET);
        }

        Function<Void,Double> trainErrorFunction = (v) -> {
            return 0d;
        };

        Function2<LocalDateTime,Double,Void> saveFunction = (datetime, score) -> {
            try {
                save(datetime,score);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        Function<Void,Double> testErrorFunction = (v) -> {
            Pair<Double,Double> results = test(wordCpc2Vec, cpcVecNet, pipelineManager.getDatasetManager().getValidationIterator());
            System.out.println(" Test score Net 1: "+results.getFirst());
            System.out.println(" Test score Net 2: "+results.getSecond());
            return (results.getFirst()+results.getSecond())/2;
        };

        final int printIterations = 100;
        final AtomicBoolean stoppingCondition = new AtomicBoolean(false);

        IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, stoppingCondition);
        wordCpc2Vec.setListeners(listener);

        DataSetIterator dataSetIterator = pipelineManager.getDatasetManager().getTrainingIterator();

        for(int i = 0; i < nEpochs; i++) {
            while(dataSetIterator.hasNext()) {
                DataSet ds = dataSetIterator.next();
                train(wordCpc2Vec,cpcVecNet,ds.getFeatures(),ds.getLabels());
                System.gc();
                if(stoppingCondition.get()) break;
            }
            if(stoppingCondition.get()) break;
            dataSetIterator.reset();
        }
        if(stoppingCondition.get()) {
            System.out.println("Stopping condition reached...");
        }
        if(!isSaved()) {
            saveFunction.apply(LocalDateTime.now(), testErrorFunction.apply(null));
        }
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    public static void syncParams(MultiLayerNetwork net1, MultiLayerNetwork net2, int lastNLayers) {
        int paramsNet1Keeps = 0;
        int paramsNet2Keeps = 0;

        for (int i = 0; i < net1.getnLayers()-lastNLayers; i++) {
            paramsNet1Keeps += net1.getLayer(i).numParams();
        }
        for (int i = 0; i < net2.getnLayers()-lastNLayers; i++) {
            paramsNet2Keeps += net2.getLayer(i).numParams();
        }

        Layer[] layers1 = net1.getLayers();
        Layer[] layers2 = net2.getLayers();
        for(int i = 0; i < lastNLayers; i++) {
            int idx1 = net1.getnLayers() - 1 - i;
            int idx2 = net2.getnLayers() - 1 - i;
            layers2[idx2] = layers1[idx1];
        }
        net2.setLayers(layers2);

        INDArray net1Params = net1.params();
        INDArray net2Params = net2.params();

        INDArray paramAvg = net1Params.get(NDArrayIndex.interval(paramsNet1Keeps, net1Params.length())).addi(net2Params.get(NDArrayIndex.interval(paramsNet2Keeps, net2Params.length()))).divi(2);

        net1Params.get(NDArrayIndex.interval(paramsNet1Keeps, net1Params.length())).assign(paramAvg);
        net2Params.get(NDArrayIndex.interval(paramsNet2Keeps, net2Params.length())).assign(paramAvg);

        net1.setParams(net1Params);
        net2.setParams(net2Params);
    }

    public static void train(MultiLayerNetwork net1, MultiLayerNetwork net2, INDArray features1, INDArray features2) {
        INDArray labels = DEFAULT_LABEL_FUNCTION.apply(features1, features2);
        net1.fit(new DataSet(features1, labels));
        net2.fit(new DataSet(features2, labels));
    }

    public static Pair<Double,Double> test(MultiLayerNetwork net1, MultiLayerNetwork net2, INDArray features1, INDArray features2) {
        INDArray labels = DEFAULT_LABEL_FUNCTION.apply(features1, features2);
        return new Pair<>(test(net1,features1,labels),test(net2,features2,labels));
    }

    public static double test(MultiLayerNetwork net, INDArray features, INDArray labels) {
        INDArray predictions = net.activateSelectedLayers(0,net.getnLayers()-1,features);
        return NDArrayHelper.sumOfCosineSimByRow(predictions,labels);
    }

    public static Pair<Double,Double> test(MultiLayerNetwork net1, MultiLayerNetwork net2, DataSetIterator iterator) {
        double d1 = 0;
        double d2 = 0;
        long count = 0;
        while(iterator.hasNext()) {
            DataSet ds = iterator.next();
            Pair<Double,Double> test = test(net1,net2,ds.getFeatures(),ds.getLabels());
            d1+=test.getFirst();
            d2+=test.getSecond();
            count++;
        }
        iterator.reset();
        if(count>0) {
            d1/=count;
            d2/=count;
        }
        return new Pair<>(d1,d2);
    }
}
