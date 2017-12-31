package models.similarity_models.combined_similarity_model;

import data_pipeline.optimize.nn_optimization.CGRefactorer;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
 * Created by Evan on 12/24/2017.
 */
public class RecurrentWordCPC2VecModel extends AbstractCombinedSimilarityModel<ComputationGraph> implements RecurrentModel{
    public static final String RECCURENT_MODEL = "recurrentWordCpc2Vec";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "recurrent_word_cpc_2_vec_model_data");

    ComputationGraph recurrentWordCpc2Vec;
    CombinedVariationalAutoencoder autoencoder;

    public RecurrentWordCPC2VecModel(RecurrentWordCPC2VecPipelineManager pipelineManager, String modelName, CombinedVariationalAutoencoder autoencoder) {
        super(pipelineManager,ComputationGraph.class,modelName);
        this.autoencoder=autoencoder;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    protected Map<String, ComputationGraph> buildNetworksForTraining() {
        int hiddenLayerSize = 48;
        int inputSize = 32;
        int outputSize = 32;
        int numHiddenLayers = 10;

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
                .setOutputs("y")
                .addLayer(String.valueOf(i), NNOptimizer.newGravesLSTMLayer(inputSize,hiddenLayerSize).build(), "x")
                .addLayer(String.valueOf(i+1), NNOptimizer.newGravesLSTMLayer(inputSize+hiddenLayerSize,hiddenLayerSize).build(), String.valueOf(i), "x");

        int increment = 1;

        i+=2;

        int t = i;
        //  hidden layers
        for(; i < t + numHiddenLayers*increment; i+=increment) {
            org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newGravesLSTMLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize);
            wordCPC2VecConf = wordCPC2VecConf.addLayer(String.valueOf(i),layer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));
        }

        // output layers
        RnnOutputLayer.Builder outputLayer = NNOptimizer.newRNNOutputLayer(hiddenLayerSize+hiddenLayerSize,outputSize).lossFunction(lossFunction);

        wordCPC2VecConf = wordCPC2VecConf.addLayer("y",outputLayer.build(), String.valueOf(i-increment), String.valueOf(i-2*increment));

        recurrentWordCpc2Vec = new ComputationGraph(wordCPC2VecConf.build());

        recurrentWordCpc2Vec.init();

        //syncParams(wordCpc2Vec,cpcVecNet,encodingIdx);

        Map<String,ComputationGraph> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());
        nameToNetworkMap.put(RECCURENT_MODEL,recurrentWordCpc2Vec);
        return nameToNetworkMap;
    }

    @Override
    protected Map<String, ComputationGraph> updateNetworksBeforeTraining(Map<String, ComputationGraph> networkMap) {
        double newLearningRate = 0.0001;
        recurrentWordCpc2Vec = CGRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(RECCURENT_MODEL),newLearningRate,false);
        Map<String,ComputationGraph> updates = Collections.synchronizedMap(new HashMap<>());
        updates.put(RECCURENT_MODEL,recurrentWordCpc2Vec);
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
            valCount+=ds.getFeatures().shape()[0];
            //System.gc();
        }

        System.out.println("Num validation datasets: "+validationDataSets.size());

        return (v) -> {
            System.gc();
            return test(recurrentWordCpc2Vec, validationDataSets.iterator());
        };
    }

    public static double test(ComputationGraph net, INDArray features, INDArray labels, INDArray featuresMask, INDArray labelsMask) {
        return 1d+net.score(new DataSet(features,labels,featuresMask,labelsMask));
    }


    public double test(ComputationGraph net1, Iterator<DataSet> iterator) {
        double d = 0;
        long count = 0;
        while(iterator.hasNext()) {
            DataSet ds = iterator.next();
            INDArray encoding = getEncodingTimeSeries(ds);
            d += test(net1,ds.getFeatures(),encoding,ds.getFeaturesMaskArray(),ds.getLabelsMaskArray());
            count++;
        }
        if(count>0) {
            d/=count;
        }
        return d;
    }

    private INDArray getEncodingTimeSeries(DataSet ds) {
        INDArray featureMean = ds.getFeatureMatrix().mean(2);
        INDArray labelsMean = ds.getLabels();
        featureMean.diviColumnVector(featureMean.norm2(1));
        labelsMean.diviColumnVector(labelsMean.norm2(1));

        INDArray encoding = autoencoder.encode(DEFAULT_LABEL_FUNCTION.apply(featureMean,labelsMean));
        encoding.diviColumnVector(encoding.norm2(1));

        INDArray labels = Nd4j.create(ds.getFeatures().shape());
        for(int i = 0; i < ds.getFeatures().shape()[2]; i++) {
            labels.put(new INDArrayIndex[]{NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(i)},encoding);
        }
        return labels;
    }

    @Override
    public void train(INDArray features, INDArray labels, INDArray featuresMask, INDArray labelsMask) {
        INDArray encoding = getEncodingTimeSeries(new DataSet(features,labels,featuresMask,labelsMask));
        train(recurrentWordCpc2Vec, features, encoding,featuresMask,labelsMask);
    }

    @Override
    protected void train(INDArray features, INDArray labels) {
       throw new UnsupportedOperationException("must use recurrent version of train()");
    }

    @Override
    protected Function<IterationListener, Void> setListenerFunction() {
        return listener -> {
            recurrentWordCpc2Vec.setListeners(listener);
            return null;
        };
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
