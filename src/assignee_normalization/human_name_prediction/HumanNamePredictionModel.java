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
import lombok.NonNull;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static data_pipeline.optimize.nn_optimization.NNOptimizer.newGravesLSTMLayer;
import static data_pipeline.optimize.nn_optimization.NNOptimizer.newRNNOutputLayer;

/**
 * Created by Evan on 11/30/2017.
 */
public class HumanNamePredictionModel extends ComputationGraphPredictionModel<Boolean> {
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"human_name_prediction_model_data/");

    private HumanNamePredictionPipelineManager pipelineManager;
    public HumanNamePredictionModel(HumanNamePredictionPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager = pipelineManager;
    }

    @Override
    public Map<String, Boolean> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        // only predicts with assignees
        int batchSize = 250;
        String[] assigneeArray = assignees.toArray(new String[assignees.size()]);
        Map<String,Boolean> predictionsMap = new HashMap<>();
        for(int i = 0; i < assigneeArray.length; i+=batchSize) {
            int r = Math.min(assigneeArray.length,i+batchSize);
            Map<String,Boolean> partial = isHuman(net,Arrays.copyOfRange(assigneeArray,i,r));
            System.out.println("Finished predicting "+r);
            predictionsMap.putAll(partial);
            System.gc();
        }
        // print map to csv
        try {
            printSampleToCSV(predictionsMap);
        }catch(Exception e) {
            e.printStackTrace();
            System.out.println("Error while printing sample to csv...");
        }
        return predictionsMap;
    }

    private Map<String,Boolean> isHuman(ComputationGraph net, @NonNull String... names) {
        List<INDArray> allMasks = new ArrayList<>();
        List<INDArray> allFeatures = new ArrayList<>();
        for(String name : names) {
            Pair<INDArray,INDArray> featuresAndMask = pipelineManager.getFeaturesAndFeatureMask(name);
            allFeatures.add(featuresAndMask.getFirst());
            allMasks.add(featuresAndMask.getSecond());
        }
        INDArray features = Nd4j.create(allFeatures.size(),allFeatures.get(0).shape()[0],allFeatures.get(0).shape()[1]);
        for(int i = 0; i < allFeatures.size(); i++) {
            features.get(NDArrayIndex.point(i),NDArrayIndex.all(),NDArrayIndex.all()).assign(allFeatures.get(i));
        }
        INDArray mask = Nd4j.vstack(allMasks);
        INDArray labelMask = Nd4j.zeros(features.shape()[0],features.shape()[2]);
        labelMask.putColumn(labelMask.columns()-1,Nd4j.ones(labelMask.rows()));
        net.setLayerMaskArrays(new INDArray[]{mask}, new INDArray[]{labelMask});
        INDArray output = net.output(false,features)[0];
        net.clearLayerMaskArrays();
        INDArray predictions = output.get(NDArrayIndex.all(),NDArrayIndex.all(),NDArrayIndex.point(output.shape()[2]-1));
        Map<String,Boolean> predictionMap = new HashMap<>();
        float[] probCompany = predictions.getColumn(0).data().asFloat();
        float[] probHuman = predictions.getColumn(1).data().asFloat();
        for(int i = 0; i < names.length; i++) {
            if (probCompany[i] > probHuman[i]) {
                //System.out.println(names[i]+" is a company!");
                predictionMap.put(names[i],false);
            } else {
                //System.out.println(names[i]+" is a human!");
                predictionMap.put(names[i],true);
            }
        }
        return predictionMap;
    }

    private static void printSampleToCSV(Map<String,Boolean> predictionMap) throws Exception {
        Random rand = new Random();
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(new File(Constants.DATA_FOLDER+"human_or_company_predictions.csv")))) {
            List<String> lines = new ArrayList<>(predictionMap.entrySet().parallelStream()
                    .map(e->{
                        String name = e.getKey();
                        if(e.getValue()) return name+",human";
                        else return name+",company";
                    }).collect(Collectors.toList()));

            for(int i = 0; i < 50000 && lines.size()>0; i++) {
                writer.write(lines.get(rand.nextInt(lines.size()))+"\n");
            }
            writer.flush();
        }
    }

    @Override
    public void train(int nEpochs) {
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
            return test(validationDataSets,net);
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
        final int hiddenLayerSize = 96;
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
            double newLearningRate = 0.0005;
            // no way to change learning rate (yet) for comp graphs

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

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    private List<List<HyperParameter>> getLayerParameters() {
        return Arrays.asList(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
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
                new LayerWrapper("l4", newGravesLSTMLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize),"l2","l3"),
                new LayerWrapper("y", newRNNOutputLayer(hiddenLayerSize+hiddenLayerSize,outputSize), "l3","l4")
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
                new LearningRateParameter(0.01,0.01),
                //new L2RegularizationParameter(1e-4,1e-4),
                new UpdaterParameter(Arrays.asList(
                        Updater.RMSPROP//,
                        //Updater.ADAM
                )),
                new ActivationFunctionParameter(Arrays.asList(
                        Activation.TANH//,
                        //Activation.LEAKYRELU
                ))
        );
    }


    private double test(List<DataSet> dataSets, ComputationGraph net) {
        isHuman(net,"hallmark, evan", "linkedin, llc", "microsoft corporation", "google", "lubitz, michael");
        Iterator<DataSet> iterator = dataSets.iterator();
        System.out.println("Train score: "+net.score());
        Evaluation eval = new Evaluation(2);
        while(iterator.hasNext()) {
            DataSet ds = iterator.next();
            INDArray labelMask = ds.getLabelsMaskArray();
            net.setLayerMaskArrays(new INDArray[]{ds.getFeaturesMaskArray()},new INDArray[]{ds.getLabelsMaskArray()});
            INDArray outputs = net.output(false,ds.getFeatures())[0];
            net.clearLayerMaskArrays();
            eval.evalTimeSeries(ds.getLabels(),outputs,labelMask);
            System.gc();
        }
        System.out.println(eval.stats());
        return 1d - eval.f1();
    }
}
