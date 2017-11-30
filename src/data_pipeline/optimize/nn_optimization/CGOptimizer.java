package data_pipeline.optimize.nn_optimization;

import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.impl.ActivationFunctionParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import data_pipeline.optimize.parameters.impl.LossFunctionParameter;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import static data_pipeline.optimize.nn_optimization.NNOptimizer.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/10/17.
 */
public class CGOptimizer {
    private NeuralNetConfiguration preModel;
    private List<VertexWrapper> vertexModels;
    private List<LayerWrapper> layerModels;
    private List<HyperParameter> modelParameters;
    private List<List<HyperParameter>> layerParameters;
    private int nSamples;
    private List<ModelWrapper> networkSamples;
    private Function<ModelWrapper<ComputationGraph>,Void> addListenerFunction;
    private boolean init = false;
    private String[] inputs;
    private String[] outputs;
    public CGOptimizer(NeuralNetConfiguration preModel, List<LayerWrapper> layerModels, List<VertexWrapper> vertexModels,
                       List<HyperParameter> modelParameters, List<List<HyperParameter>> layerParameters, int nSamples, Function<ModelWrapper<ComputationGraph>,Void> addListenerFunction, String[] inputs, String[] outputs) {
        this.preModel=preModel;
        this.vertexModels=vertexModels;
        this.inputs=inputs;
        this.outputs=outputs;
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
            ComputationGraph net = buildNetworkWithHyperParameters(
                    preModel,
                    layerModels,
                    vertexModels,
                    newModelParams,
                    newLayerParams,
                    inputs,
                    outputs
            );
            ModelWrapper netWrap = new ModelWrapper<>(net,flattenParams(newModelParams,newLayerParams));
            addListenerFunction.apply(netWrap);
            networkSamples.add(netWrap);
            System.out.println("Initialized model-"+i+": "+netWrap.describeHyperParameters());
        }
    }

    public void train(DataSet ds) {
        if(!init) throw new RuntimeException("Must initialize optimizer with call to 'initNetworkSamples();'");
        (networkSamples.size()>1 ? networkSamples.parallelStream() : networkSamples.stream())
                .filter(net->net.isKeepTraining())
                .forEach(net->{
                    try {
                        ((ComputationGraph)net.getNet()).fit(ds);
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

    private static ComputationGraph buildNetworkWithHyperParameters(
            NeuralNetConfiguration preModel, List<LayerWrapper> layerModels, List<VertexWrapper> vertexModels,
            List<HyperParameter> modelParameters, List<List<HyperParameter>> layerParameters, String[] inputs, String[] outputs)
    {
        if(layerModels.size()!=layerParameters.size()) throw new RuntimeException("layer models and layer parameters must have the same size.");
        NeuralNetConfiguration.Builder newModelConf = new NeuralNetConfiguration.Builder(preModel.clone());
        for (HyperParameter<?> hyperParameter : modelParameters) {
            newModelConf = hyperParameter.applyToNetwork(newModelConf);
        }
        ComputationGraphConfiguration.GraphBuilder networkLayerBuilder = newModelConf.graphBuilder()
                .addInputs(inputs)
                .setOutputs(outputs);
        for(int i = 0; i < layerModels.size(); i++) {
            System.out.println("Updating layer "+(i+1)+" of "+layerModels.size());
            LayerWrapper layerModel = layerModels.get(i);
            Layer.Builder layerBuilder = layerModel.getVertex();
            for (HyperParameter<?> hyperParameter : layerParameters.get(i)) {
                layerBuilder = hyperParameter.applyToLayer(layerBuilder);
            }
            networkLayerBuilder = networkLayerBuilder.addLayer(layerModel.getName(),layerBuilder.build().clone(),layerModel.getInputs());
        }
        for(int i = 0; i < vertexModels.size(); i++) {
            VertexWrapper vertexModel = vertexModels.get(i);
            networkLayerBuilder = networkLayerBuilder.addVertex(vertexModel.getName(),vertexModel.getVertex().clone(),vertexModel.getInputs());
        }

        // swap out configs
        ComputationGraph net = new ComputationGraph(networkLayerBuilder.build().clone());
        net.init();
        return net;
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
        final int outputSize = 2;
        final int inputSize = 30000;
        final String[] inputs = new String[]{"inputs"};
        final String[] outputs = new String[]{"output"};

        List<VertexWrapper> vertexModels = Arrays.asList(
                new VertexWrapper("v3",new MergeVertex(),"v1","v2")
        );


        List<LayerWrapper> layerModels = Arrays.asList(
                new LayerWrapper("v1",newDenseLayer(inputSize,hiddenLayerSize),inputs),
                new LayerWrapper("v2",newDenseLayer(inputSize,hiddenLayerSize), inputs),
                new LayerWrapper("output",newOutputLayer(hiddenLayerSize,outputSize),"v3")
        );

        Function<ModelWrapper<ComputationGraph>,Void> addListenerFunction = net -> {
            DefaultScoreListener listener = null; //new OptimizationScoreListener(printIterations, )
            net.getNet().setListeners(listener);
            return null;
        };


        // Here is the actual optimizer instance
        CGOptimizer optimizer = new CGOptimizer(
                defaultNetworkConfig(),
                layerModels,
                vertexModels,
                modelHyperParameters,
                layerParameters,
                5,
                addListenerFunction,
                inputs,
                outputs
        );

        optimizer.initNetworkSamples();

    }
}
