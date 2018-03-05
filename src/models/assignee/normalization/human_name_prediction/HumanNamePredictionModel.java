package models.assignee.normalization.human_name_prediction;

import data_pipeline.models.ComputationGraphPredictionModel;
import data_pipeline.optimize.nn_optimization.LayerWrapper;
import data_pipeline.optimize.nn_optimization.VertexWrapper;
import data_pipeline.optimize.parameters.HyperParameter;
import data_pipeline.optimize.parameters.impl.ActivationFunctionParameter;
import data_pipeline.optimize.parameters.impl.LearningRateParameter;
import data_pipeline.optimize.parameters.impl.LossFunctionParameter;
import data_pipeline.optimize.parameters.impl.UpdaterParameter;
import lombok.NonNull;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.PairBackup;
import seeding.Constants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

import static data_pipeline.optimize.nn_optimization.NNOptimizer.*;

/**
 * Created by Evan on 11/30/2017.
 */
public class HumanNamePredictionModel extends ComputationGraphPredictionModel<Boolean> {
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"human_name_prediction_model_data/");

    public HumanNamePredictionModel(HumanNamePredictionPipelineManager pipelineManager, String modelName) {
        super(modelName,pipelineManager);
    }

    @Override
    public Map<String, Boolean> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        // only predicts with assignees
        int batchSize = 1000;
        String[] assigneeArray = assignees.toArray(new String[assignees.size()]);
        Map<String,Boolean> predictionsMap = Collections.synchronizedMap(new HashMap<>());
        for(int i = 0; i < assigneeArray.length; i+=batchSize) {
            int r = Math.min(assigneeArray.length,i+batchSize);
            Map<String,Boolean> partial = isHuman(net,Arrays.copyOfRange(assigneeArray,i,r));
            System.out.println("Finished predicting "+r+" / "+assigneeArray.length);
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

    public Map<String,Boolean> isHuman(ComputationGraph net, @NonNull String... names) {
        List<INDArray> allMasks = new ArrayList<>();
        List<INDArray> allFeatures = new ArrayList<>();
        for(String name : names) {
            PairBackup<INDArray,INDArray> featuresAndMask = ((HumanNamePredictionPipelineManager)pipelineManager).getFeaturesAndFeatureMask(name);
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
        Map<String,Boolean> predictionMap = Collections.synchronizedMap(new HashMap<>());
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

    public static void printSampleToCSV(Map<String,Boolean> predictionMap) throws Exception {
        Random rand = new Random();
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(new File(Constants.DATA_FOLDER+"human_or_company_predictions.csv")))) {
            List<String> lines = new ArrayList<>(predictionMap.entrySet().parallelStream()
                    .map(e->{
                        String name = e.getKey();
                        if(e.getValue()) return "\""+name+"\",human";
                        else return "\""+name+"\",company";
                    }).collect(Collectors.toList()));

            for(int i = 0; i < 50000 && lines.size()>0; i++) {
                writer.write(lines.get(rand.nextInt(lines.size()))+"\n");
            }
            writer.flush();
        }
    }


    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    @Override
    protected List<List<HyperParameter>> getLayerParameters() {
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


    @Override
    protected List<LayerWrapper> getLayerModels(int inputSize, int hiddenLayerSize, int outputSize) {
        return Arrays.asList(
                new LayerWrapper("l1", newGravesLSTMLayer(inputSize,hiddenLayerSize), "x"),
                new LayerWrapper("l2", newGravesLSTMLayer(inputSize+hiddenLayerSize,hiddenLayerSize), "x","l1"),
                new LayerWrapper("l3", newGravesLSTMLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize),"l1","l2"),
                new LayerWrapper("l4", newGravesLSTMLayer(hiddenLayerSize+hiddenLayerSize,hiddenLayerSize),"l2","l3"),
                new LayerWrapper("y", newRNNOutputLayer(hiddenLayerSize+hiddenLayerSize,outputSize), "l3","l4")
        );
    }

    @Override
    protected List<VertexWrapper> getVertexModels() {
        return Arrays.asList(
                //new VertexWrapper("rnn_to_dense1",new PreprocessorVertex(new RnnToFeedForwardPreProcessor()),"l3"),
                //new VertexWrapper("rnn_to_dense2",new PreprocessorVertex(new RnnToFeedForwardPreProcessor()),"l4")
        );
    }


    @Override
    protected List<HyperParameter> getModelParameters() {
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

    @Override
    protected int getNumNetworks() {
        return 1;
    }

    @Override
    protected int getHiddenLayerSize() {
        return 96;
    }

    @Override
    protected double getNewLearningRate() {
        return 0.0005;
    }

    @Override
    public int getPrintIterations() {
        return 100;
    }

    @Override
    protected double test(List<DataSet> dataSets, ComputationGraph net, boolean timeseries) {
        isHuman(net,"hallmark, evan", "linkedin, llc", "microsoft corporation", "google", "lubitz, michael");
        return super.test(dataSets,net,timeseries);
    }
}
