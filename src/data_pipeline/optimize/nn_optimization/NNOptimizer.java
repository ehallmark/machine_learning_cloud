package data_pipeline.optimize.nn_optimization;

import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.impl.ActivationFunctionParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import data_pipeline.optimize.parameters.impl.LossFunctionParameter;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/10/17.
 */
public class NNOptimizer {
    private NeuralNetConfiguration preModel;
    private List<Layer.Builder> layerModels;
    private List<HyperParameter> modelParameters;
    private List<List<HyperParameter>> layerParameters;
    private int nSamples;
    private List<MultiLayerNetworkWrapper> networkSamples;
    private Function<MultiLayerNetworkWrapper,Void> addListenerFunction;
    private boolean init = false;
    public NNOptimizer(NeuralNetConfiguration preModel, List<Layer.Builder> layerModels,
                       List<HyperParameter> modelParameters, List<List<HyperParameter>> layerParameters, int nSamples, Function<MultiLayerNetworkWrapper,Void> addListenerFunction) {
        this.preModel=preModel;
        this.layerModels=layerModels;
        this.modelParameters=modelParameters;
        this.layerParameters=layerParameters;
        this.nSamples=nSamples;
        this.networkSamples = Collections.synchronizedList(new ArrayList<>(nSamples));
        this.addListenerFunction=addListenerFunction;
    }


    public void initNetworkSamples() {
        if(init) throw new RuntimeException("Samples have already been initialized.");
        init = true;
        for(int i = 0; i < nSamples; i++) {
            List<HyperParameter> newModelParams = modelParameters.stream().map(param->param.mutate()).collect(Collectors.toList());
            List<List<HyperParameter>> newLayerParams = layerParameters.stream()
                    .map(paramList->paramList.stream().map(param->param.mutate()).collect(Collectors.toList()))
                    .collect(Collectors.toList());
            MultiLayerNetwork net = buildNetworkWithHyperParameters(
                    preModel,
                    layerModels,
                    newModelParams,
                    newLayerParams
            );
            MultiLayerNetworkWrapper netWrap = new MultiLayerNetworkWrapper(net,flattenParams(newModelParams,newLayerParams));
            addListenerFunction.apply(netWrap);
            networkSamples.add(netWrap);
            System.out.println("Initialized model-"+i+": "+netWrap.describeHyperParameters());
        }
    }

    public void train(DataSet ds) {
        if(!init) throw new RuntimeException("Must initialize optimizer with call to 'initNetworkSamples();'");
        networkSamples.parallelStream()
                .filter(net->net.isKeepTraining())
                .forEach(net->{
                    try {
                        net.getNet().fit(ds);
                    } catch(StoppingConditionMetException e) {
                        // stop training particular model
                        System.out.println("hit stopping condition for: "+net.describeHyperParameters());
                        net.setKeepTraining(false);
                    }
                });
    }

    private static List<HyperParameter> flattenParams(List<HyperParameter> modelParameters, List<List<HyperParameter>> layerParameters) {
        return Stream.of(modelParameters,layerParameters.stream().flatMap(list->list.stream()).collect(Collectors.toList()))
                .flatMap(list->list.stream()).collect(Collectors.toList());
    }

    private static MultiLayerNetwork buildNetworkWithHyperParameters(
            NeuralNetConfiguration preModel, List<Layer.Builder> layerModels,
            List<HyperParameter> modelParameters, List<List<HyperParameter>> layerParameters)
    {
        if(layerModels.size()!=layerParameters.size()) throw new RuntimeException("layer models and layer parameters must have the same size.");
        NeuralNetConfiguration.Builder newModelConf = new NeuralNetConfiguration.Builder(preModel.clone());
        for (HyperParameter<?> hyperParameter : modelParameters) {
            newModelConf = hyperParameter.applyToNetwork(newModelConf);
        }
        NeuralNetConfiguration.ListBuilder networkLayerBuilder = newModelConf.list();
        for(int i = 0; i < layerModels.size(); i++) {
            System.out.println("Updating layer "+(i+1)+" of "+layerModels.size());
            Layer.Builder layer = layerModels.get(i);
            for (HyperParameter<?> hyperParameter : layerParameters.get(i)) {
                layer = hyperParameter.applyToLayer(layer);
            }
            networkLayerBuilder = networkLayerBuilder.layer(i, layer.build().clone());
        }
        // swap out configs
        MultiLayerNetwork net = new MultiLayerNetwork(networkLayerBuilder.build().clone());
        net.init();
        return net;
    }

    public static DenseLayer.Builder newDenseLayer(int nIn, int nOut) {
        return new DenseLayer.Builder().nIn(nIn).nOut(nOut);
    }

    public static BatchNormalization.Builder newBatchNormLayer(int nIn, int nOut) {
        return new BatchNormalization.Builder().nIn(nIn).nOut(nOut).minibatch(true);
    }

    public static OutputLayer.Builder newOutputLayer(int nIn, int nOut) {
        return new OutputLayer.Builder().nIn(nIn).nOut(nOut);
    }

    public static NeuralNetConfiguration defaultNetworkConfig() {
        return new NeuralNetConfiguration.Builder()
                .miniBatch(true)
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .build();
    }

    public static void main(String[] args) {
        List<HyperParameter> modelHyperParameters = Arrays.asList(
                new LearningRateParameter(0.0001,0.1),
                new ActivationFunctionParameter(Arrays.asList(
                        Activation.LEAKYRELU,
                        Activation.RRELU,
                        Activation.HARDTANH,
                        Activation.TANH
                ))
        );

        List<List<HyperParameter>> layerParameters = Arrays.asList(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                // output layer
                Arrays.asList(
                        new ActivationFunctionParameter(Arrays.asList(
                                Activation.IDENTITY,
                                Activation.TANH,
                                Activation.HARDTANH
                        )),
                        new LossFunctionParameter(Arrays.asList(
                                LossFunctions.LossFunction.COSINE_PROXIMITY,
                                LossFunctions.LossFunction.MCXENT,
                                LossFunctions.LossFunction.MSE,
                                LossFunctions.LossFunction.KL_DIVERGENCE
                        ))
                )
        );

        final int hiddenLayerSize = 512;
        final int outputSize = CPCVariationalAutoEncoderNN.VECTOR_SIZE;
        final int inputSize = 30000;

        List<Layer.Builder> layerBuilders = Arrays.asList(
                newDenseLayer(inputSize,hiddenLayerSize),
                newBatchNormLayer(hiddenLayerSize,hiddenLayerSize),
                newDenseLayer(hiddenLayerSize,hiddenLayerSize),
                newBatchNormLayer(hiddenLayerSize,hiddenLayerSize),
                newOutputLayer(hiddenLayerSize,outputSize)
        );

        Function<MultiLayerNetworkWrapper,Void> addListenerFunction = net -> {
            DefaultScoreListener listener = null; //new OptimizationScoreListener(printIterations, )
            net.getNet().setListeners(listener);
            return null;
        };


        // Here is the actual optimizer instance
        NNOptimizer optimizer = new NNOptimizer(
                defaultNetworkConfig(),
                layerBuilders,
                modelHyperParameters,
                layerParameters,
                5,
                addListenerFunction
        );

        optimizer.initNetworkSamples();

        optimizer.networkSamples.forEach(net->{
            System.out.println("Net: "+net.describeHyperParameters());
        });
    }
}
