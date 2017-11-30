package assignee_normalization.human_name_prediction;

import data_pipeline.helpers.Function3;
import data_pipeline.models.ComputationGraphPredictionModel;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.MultiScoreReporter;
import data_pipeline.models.listeners.OptimizationScoreListener;
import data_pipeline.optimize.nn_optimization.*;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.impl.ActivationFunctionParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import data_pipeline.optimize.parameters.impl.LossFunctionParameter;
import data_pipeline.optimize.parameters.impl.UpdaterParameter;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static data_pipeline.optimize.nn_optimization.NNOptimizer.newGravesLSTMLayer;
import static data_pipeline.optimize.nn_optimization.NNOptimizer.newRNNOutputLayer;

/**
 * Created by Evan on 11/30/2017.
 */
public class HumanNamePredictionModel extends ComputationGraphPredictionModel<INDArray> {
    public static final int VECTOR_SIZE = 128;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"human_name_prediction_model_data/");

    private HumanNamePredictionPipelineManager pipelineManager;
    public HumanNamePredictionModel(HumanNamePredictionPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager = pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        // only predicts with assignees

        // assignees.forEach(->assignee ...
        return null;
    }

    @Override
    public void train(int nEpochs) {
        final int printIterations = 100;
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        DataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();

        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        Function<ComputationGraph,Double> testErrorFunction = (net) -> {
            return test(validationIterator,net);
        };

        Function3<ComputationGraph,LocalDateTime,Double,Void> saveFunction = (net, datetime, score) -> {
            try {
                save(datetime,score,net);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        // Optimizer
        int numNetworks = 1;
        final int outputSize = 2;
        final int inputSize = pipelineManager.inputSize();
        final int hiddenLayerSize = 64;
        final MultiScoreReporter reporter = new MultiScoreReporter(numNetworks, 3);
        final String[] inputs = new String[]{"x"};
        final String[] outputs = new String[]{"y"};
        if(net==null) {
            CGOptimizer optimizer;
            optimizer = new CGOptimizer(
                    getPreModel(),
                    getLayerModels(inputSize, hiddenLayerSize, outputSize),
                    getVertexModels(),
                    getModelParameters(),
                    getLayerParameters(),
                    numNetworks,
                    net -> {
                        IterationListener listener = new OptimizationScoreListener<>(reporter, net, printIterations, testErrorFunction, saveFunction);
                        net.getNet().setListeners(listener);
                        return null;
                    },
                    inputs,
                    outputs
            );
            // initialize optimizer
            optimizer.initNetworkSamples();


            for (int i = 0; i < nEpochs; i++) {
                System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
                while(trainIter.hasNext()) {
                    try {
                        optimizer.train(trainIter.next());
                    } catch (StoppingConditionMetException s) {
                        System.out.println("Stopping condition met");
                    }
                    if (stoppingCondition.get()) {
                        break;
                    }
                }
                if(stoppingCondition.get()) break;
                trainIter.reset();
            }
        } else {
            double newLearningRate = 0.001;
            // no way to change learning rate (yet) for comp graphs

            System.out.println("Conf: "+net.getConfiguration().toYaml());

            ModelWrapper<ComputationGraph> netWrapper = new ModelWrapper<>(net, Collections.emptyList());
            IterationListener listener = new OptimizationScoreListener<>(reporter, netWrapper, printIterations, testErrorFunction, saveFunction);
            net.setListeners(listener);
            for (int i = 0; i < nEpochs; i++) {
                System.out.println("Starting epoch {" + (i + 1) + "} of {" + nEpochs + "}");
                try {
                    net.fit(trainIter);
                } catch (StoppingConditionMetException s) {
                    System.out.println("Stopping condition met");
                }
                if (stoppingCondition.get()) {
                    break;
                }
                trainIter.reset();
            }
        }
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    private List<List<HyperParameter>> getLayerParameters() {
        return Arrays.asList(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
               // Collections.emptyList(),
                // output layer
                Arrays.asList(
                        new ActivationFunctionParameter(Arrays.asList(
                                Activation.SOFTMAX
                        )),
                        new LossFunctionParameter(Arrays.asList(
                                LossFunctions.LossFunction.XENT
                        ))
                )
        );
    }

    private NeuralNetConfiguration getPreModel() {
        return NNOptimizer.defaultNetworkConfig();
    }

    private List<LayerWrapper> getLayerModels(int inputSize, int hiddenLayerSize, int outputSize) {
        return Arrays.asList(
                new LayerWrapper("l1", newGravesLSTMLayer(inputSize,hiddenLayerSize), "x"),
                new LayerWrapper("l2", newGravesLSTMLayer(inputSize+hiddenLayerSize,hiddenLayerSize), "x","l1"),
                new LayerWrapper("l3", newGravesLSTMLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize),"l1","l2"),
                //new LayerWrapper("l4", newGravesLSTMLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize),"l2","l3"),
                new LayerWrapper("y", newRNNOutputLayer(hiddenLayerSize+hiddenLayerSize,outputSize), "l2","l3")
        );
    }

    private List<VertexWrapper> getVertexModels() {
        return Arrays.asList(
                //new VertexWrapper("rnn_to_dense1",new PreprocessorVertex(new RnnToFeedForwardPreProcessor()),"l3"),
                //new VertexWrapper("rnn_to_dense2",new PreprocessorVertex(new RnnToFeedForwardPreProcessor()),"l4")
        );
    }


    private List<HyperParameter> getModelParameters() {
        return Arrays.asList(
                new LearningRateParameter(0.001,0.15),
                new UpdaterParameter(Arrays.asList(
                        Updater.RMSPROP,
                        Updater.ADAM
                )),
                new ActivationFunctionParameter(Arrays.asList(
                        Activation.TANH,
                        Activation.LEAKYRELU
                ))
        );
    }


    private double test(DataSetIterator iterator, ComputationGraph net) {
        System.out.println("Train score: "+net.score());
        Evaluation eval = new Evaluation(2);
        while(iterator.hasNext()) {
            DataSet ds = iterator.next();
            INDArray labelMask = ds.getLabelsMaskArray();
            net.setLayerMaskArrays(new INDArray[]{ds.getFeaturesMaskArray()},new INDArray[]{ds.getLabelsMaskArray()});
            INDArray outputs = net.output(false,ds.getFeatures())[0];

            System.out.println("Shape of actual labels: "+ds.getLabels().shapeInfoToString());
            System.out.println("Shape of predicted labels: "+outputs.shapeInfoToString());
            eval.evalTimeSeries(ds.getLabels(),outputs,labelMask);
        }
        System.out.println(eval.stats());
        return 1d - eval.f1();
    }
}
