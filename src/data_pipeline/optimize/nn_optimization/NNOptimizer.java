package data_pipeline.optimize.nn_optimization;

import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.impl.ActivationFunctionParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import data_pipeline.optimize.parameters.impl.LossFunctionParameter;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.Layer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/10/17.
 */
public class NNOptimizer {
    private NeuralNetConfiguration preModel;
    private List<Layer.Builder> layerModels;
    private List<HyperParameter<?>> modelParameters;
    private List<List<HyperParameter<?>>> layerParameters;
    private List<HyperParameter<?>> allHyperParameters;
    private int nSamples;
    private List<MultiLayerNetwork> networkSamples;
    public NNOptimizer(NeuralNetConfiguration preModel, List<Layer.Builder> layerModels,
                       List<HyperParameter<?>> modelParameters, List<List<HyperParameter<?>>> layerParameters, int nSamples) {
        this.preModel=preModel;
        this.layerModels=layerModels;
        this.modelParameters=modelParameters;
        this.layerParameters=layerParameters;
        this.nSamples=nSamples;
        this.networkSamples = Collections.synchronizedList(new ArrayList<>(nSamples));

        this.allHyperParameters = Stream.of(modelParameters,layerParameters.stream().flatMap(list->list.stream()).collect(Collectors.toList()))
                .flatMap(list->list.stream()).collect(Collectors.toList());
    }

    public void initNetworkSamples() {
        for(int i = 0; i < nSamples; i++) {
            networkSamples.add(buildNetworkWithHyperParameters(
                    preModel,
                    layerModels,
                    modelParameters,
                    layerParameters
            ));
        }
    }

    protected static MultiLayerNetwork buildNetworkWithHyperParameters(
            NeuralNetConfiguration preModel, List<Layer.Builder> layerModels,
            List<HyperParameter<?>> modelParameters, List<List<HyperParameter<?>>> layerParameters)
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
            networkLayerBuilder = networkLayerBuilder.layer(i, layer.build());
        }
        // swap out configs
        MultiLayerNetwork net = new MultiLayerNetwork(networkLayerBuilder.build().clone());
        net.init();
        return net;
    }

    static Layer.Builder newDenseLayer(int nIn, int nOut) {
        return new DenseLayer.Builder().nIn(nIn).nOut(nOut);
    }

    static Layer.Builder newOutputLayer(int nIn, int nOut) {
        return new OutputLayer.Builder().nIn(nIn).nOut(nOut);
    }

    static NeuralNetConfiguration newNeuralNetworkConfig() {
        return new NeuralNetConfiguration.Builder()
                .miniBatch(true)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .build();
    }

    public static void main(String[] args) {
        List<HyperParameter<?>> modelHyperParameters = Arrays.asList(
                new LearningRateParameter(0.0001,0.25),
                new ActivationFunctionParameter(Arrays.asList(
                        Activation.LEAKYRELU,
                        Activation.TANH
                ))
        );

        List<List<HyperParameter<?>>> layerParameters = Arrays.asList(
                Arrays.asList(
                        new ActivationFunctionParameter(Arrays.asList(
                                Activation.HARDTANH
                        ))
                ),
                Arrays.asList(
                        new ActivationFunctionParameter(Arrays.asList(
                                Activation.HARDTANH
                        ))
                ),
                Arrays.asList(
                        new ActivationFunctionParameter(Arrays.asList(
                                Activation.HARDSIGMOID
                        )),
                        new LossFunctionParameter(Arrays.asList(
                                LossFunctions.LossFunction.COSINE_PROXIMITY,
                                LossFunctions.LossFunction.MCXENT,
                                LossFunctions.LossFunction.MSE,
                                LossFunctions.LossFunction.KL_DIVERGENCE
                        ))
                )
        );

        List<Layer.Builder> layerBuilders = Arrays.asList(
                newDenseLayer(10,20),
                newDenseLayer(20,30),
                newOutputLayer(30,2)
        );


        // Here is the actual optimizer instance
        NNOptimizer optimizer = new NNOptimizer(
                newNeuralNetworkConfig(),
                layerBuilders,
                modelHyperParameters,
                layerParameters,
                5
        );

        optimizer.initNetworkSamples();

        optimizer.networkSamples.forEach(net->{
            System.out.println("Net: "+net.getLayerWiseConfigurations().toYaml());
        });
    }
}
