package data_pipeline.models;

import data_pipeline.helpers.Function3;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.MultiScoreReporter;
import data_pipeline.models.listeners.OptimizationScoreListener;
import data_pipeline.optimize.nn_optimization.*;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.pipeline_manager.PipelineManager;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Created by ehallmark on 11/8/17.
 */
public abstract class ComputationGraphPredictionModel<T> extends BaseTrainablePredictionModel<T,ComputationGraph> {

    protected PipelineManager<DataSetIterator,?> pipelineManager;
    protected ComputationGraphPredictionModel(String modelName, PipelineManager<DataSetIterator,?> pipelineManager) {
        super(modelName);
        this.pipelineManager=pipelineManager;
    }

    public abstract File getModelBaseDirectory();

    @Override
    protected void saveNet(ComputationGraph net, File file) throws IOException {
        ModelSerializer.writeModel(net,file,true);
    }

    @Override
    protected void restoreFromFile(File modelFile) throws IOException {
        if(modelFile!=null&&modelFile.exists()) {
            this.net = ModelSerializer.restoreComputationGraph(modelFile, true);
            this.isSaved.set(true);
        } else {
            System.out.println("WARNING: Model file does not exist: "+modelFile.getAbsolutePath());
        }
    }

    protected NeuralNetConfiguration getPreModel() {
        return NNOptimizer.defaultNetworkConfig();
    }
    protected abstract List<List<HyperParameter>> getLayerParameters();
    protected abstract List<LayerWrapper> getLayerModels(int inputSize, int hiddenLayerSize, int outputSize);
    protected abstract List<VertexWrapper> getVertexModels();
    protected abstract List<HyperParameter> getModelParameters();
    protected abstract int getNumNetworks();
    protected abstract int getHiddenLayerSize();
    protected abstract double getNewLearningRate();

    protected double test(List<DataSet> dataSets, ComputationGraph net, boolean timeseries) {
        Iterator<DataSet> iterator = dataSets.iterator();
        System.out.println("Train score: "+net.score());
        Evaluation eval = new Evaluation(2);
        while(iterator.hasNext()) {
            DataSet ds = iterator.next();
            if(timeseries) {
                net.setLayerMaskArrays(new INDArray[]{ds.getFeaturesMaskArray()}, new INDArray[]{ds.getLabelsMaskArray()});
            }
            INDArray outputs = net.output(false,ds.getFeatures())[0];
            if(timeseries) {
                net.clearLayerMaskArrays();
                eval.evalTimeSeries(ds.getLabels(),outputs,ds.getLabelsMaskArray());
            } else {
                eval.eval(ds.getLabels(),outputs);
            }
            System.gc();
        }
        System.out.println(eval.stats());
        return 1d - eval.f1();
    }

    @Override
    public void train(int nEpochs) {
        train(nEpochs,getNumNetworks(),getHiddenLayerSize(),getNewLearningRate());
    }

    protected void train(int nEpochs, int numNetworks, int hiddenLayerSize, double newLearningRate) {
        final int printIterations = 100;
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        DataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();

        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        System.out.println("Loading validation iterator...");
        List<DataSet> validationDataSets = new ArrayList<>();
        while(validationIterator.hasNext()) {
            validationDataSets.add(validationIterator.next());
        }

        System.out.println("Done.");
        Function<ComputationGraph,Double> testErrorFunction = (net) -> {
            return test(validationDataSets,net,validationDataSets.get(0).getFeatureMatrix().shape().length>2);
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
        final int outputSize = validationIterator.totalOutcomes();
        final int inputSize = validationIterator.inputColumns();
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
            // initial test
            optimizer.getNetworkSamples().forEach(networkSample->{
                testErrorFunction.apply(networkSample.getNet());
            });

            for (int i = 0; i < nEpochs; i++) {
                System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
                while(trainIter.hasNext()) {
                    try {
                        optimizer.train(trainIter.next());
                    } catch (StoppingConditionMetException s) {
                        System.out.println("Stopping condition met");
                        stoppingCondition.set(true);
                    }
                    if (stoppingCondition.get()) {
                        break;
                    }

                }
                if(stoppingCondition.get()) break;
                trainIter.reset();
            }

            // set net to most recent one
            try {
                loadMostRecentModel();
            } catch(Exception e) {
                e.printStackTrace();
            }

        } else {

            net = CGRefactorer.updateNetworkLearningRate(net,newLearningRate,false);
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

}
