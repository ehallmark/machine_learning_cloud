package models.dl4j_neural_nets.classification;

import models.dl4j_neural_nets.iterators.datasets.ClassificationVectorDataSetIterator;
import models.dl4j_neural_nets.listeners.CustomAutoEncoderListener;
import models.dl4j_neural_nets.vectorization.auto_encoders.AutoEncoderModel;
import models.model_testing.SplitModelData;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;
import models.similarity_models.class_vectors.WIPOSimilarityFinder;
import models.similarity_models.class_vectors.vectorizer.ClassVectorizer;
import models.classification_models.NaiveGatherClassifier;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 6/19/17.
 */
public class WIPOToGatherModel {
    private static final File modelFile = new File("wipo_to_gather_neural_net.jobj");

    public static void trainAndSave(Map<String,INDArray> wipoLookupTable, Map<String, INDArray> gatherLookupTable, Collection<String> items, double testRatio, int batchSize, int nEpochs, File file) {
        // Get Items
        List<String> examples = new ArrayList<>(items);
        final int numTests = (int) Math.round(testRatio*examples.size());
        Collections.shuffle(examples);
        int printIterations = 1000;

        // Split data
        List<String> testSet = examples.subList(0,numTests).stream().filter(patent->wipoLookupTable.containsKey(patent)&&gatherLookupTable.containsKey(patent)).collect(Collectors.toList());
        examples=examples.subList(numTests,examples.size()).stream().filter(patent->wipoLookupTable.containsKey(patent)&&gatherLookupTable.containsKey(patent)).collect(Collectors.toList());

        // Get Classifications
        final int numInputs = wipoLookupTable.values().stream().findAny().get().length();
        final int numOutputs = gatherLookupTable.values().stream().findAny().get().length();
        final int hiddenLayerSize = (numInputs+numOutputs)/2;
        System.out.println("Num Inputs: "+numInputs);
        System.out.println("Num Outputs: "+numOutputs);
        System.out.println("Num Examples: "+examples.size());
        System.out.println("Num Tests: "+testSet.size());


        // Get Iterator
        DataSetIterator iterator = new ClassificationVectorDataSetIterator(examples, wipoLookupTable, gatherLookupTable, numInputs, numOutputs, batchSize);

        // Config
        System.out.println("Build model....");
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(69)
                .iterations(1)
                .learningRate(0.01)
                .miniBatch(true)
                .updater(Updater.ADAGRAD)
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(numInputs)
                        .nOut(hiddenLayerSize)
                        .activation(Activation.RELU).build())
                .layer(1, new DenseLayer.Builder()
                        .nIn(hiddenLayerSize)
                        .nOut(hiddenLayerSize)
                        .activation(Activation.RELU).build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .activation(Activation.SOFTMAX)
                        .nIn(hiddenLayerSize)
                        .nOut(numOutputs).build())
                .pretrain(false).backprop(true)
                .build();

        // Build and train network
        MultiLayerNetwork network = new MultiLayerNetwork(conf);
        network.init();
        network.setListeners(new CustomAutoEncoderListener(printIterations));

        INDArray testMatrix = Nd4j.create(testSet.size(),numInputs);
        INDArray testLabels = Nd4j.create(testSet.size(), numOutputs);
        for(int i = 0; i <testSet.size(); i++) {
            testMatrix.putRow(i,wipoLookupTable.get(testSet.get(i)));
            testLabels.putRow(i,gatherLookupTable.get(testSet.get(i)));
        }


        System.out.println("Train model....");
        double bestErrorSoFar = Double.MAX_VALUE;
        Double startingError = null;
        List<Double> errorList = new ArrayList<>(nEpochs);
        for( int i=0; i<nEpochs; i++ ) {
            System.out.println("*** STARTING epoch {"+i+"} ***");
            network.fit(iterator);
            System.out.println("*** STARTING TESTS ***");

            INDArray predictions = network.output(testMatrix,false);

            double error = Transforms.pow(testLabels.sub(predictions),2d,false).meanNumber().doubleValue();

            errorList.add(error);
            if(startingError==null) startingError=error;
            if(error<bestErrorSoFar){
                bestErrorSoFar=error;
                System.out.println("FOUND BETTER MODEL");
                AutoEncoderModel.saveModel(network,file);
                System.out.println("Saved.");

            }
            System.out.println("Starting error: "+startingError);
            System.out.println("Avg error: "+errorList.stream().collect(Collectors.averagingDouble(d->d)));
            System.out.println("Current model error: "+error);
            System.out.println("Best error So Far: "+bestErrorSoFar);
            System.out.println("*** FINISHED epoch {"+i+"} ***");

        }
        System.out.println("****************Model finished********************");
    }

    public static void main(String[] args) {
        Map<String,INDArray> wipoLookupTable = WIPOSimilarityFinder.getRawLookupTable();

        Map<String,Collection<String>> gatherTechMap = SplitModelData.getBroadDataMap(SplitModelData.trainFile);
        ClassVectorizer vectorizer = new ClassVectorizer(NaiveGatherClassifier.invert(gatherTechMap));

        List<String> gatherPatents = gatherTechMap.entrySet().stream()
                .flatMap(e->e.getValue().stream()).distinct().collect(Collectors.toList());

        List<String> orderedTechnologies = new ArrayList<>(gatherTechMap.keySet());
        orderedTechnologies.forEach(tech->{
            System.out.println("TECHNOLOGY: "+tech);
        });

        Map<String,INDArray> gatherLookupTable = gatherPatents.stream()
                .collect(Collectors.toMap(p->p,p->Nd4j.create(vectorizer.classVectorForPatents(Arrays.asList(p),orderedTechnologies,-1))));

        gatherLookupTable.forEach((p,vec)->{
            System.out.println(p+": "+vec);
        });

        // Fetch pre data
        int batchSize = 5;
        final int nEpochs = 100;
        trainAndSave(wipoLookupTable, gatherLookupTable, gatherPatents,0.3,batchSize,nEpochs,modelFile);
    }
}
