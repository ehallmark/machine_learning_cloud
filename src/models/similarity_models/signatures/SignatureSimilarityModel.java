package models.similarity_models.signatures;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import models.dl4j_neural_nets.iterators.datasets.AsyncDataSetIterator;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.RBM;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/26/17.
 */
public class SignatureSimilarityModel {
    public static final int VECTOR_SIZE = 30;
    public static final int MAX_CPC_DEPTH = 4;
    public static final File networkFile = new File(Constants.DATA_FOLDER+"signature_neural_network.jobj");

    private CPCHierarchy hierarchy;
    private Map<String,? extends Collection<CPC>> cpcMap;
    private List<String> allAssets;
    private List<String> testAssets;
    private List<String> smallTestSet;
    private List<String> trainAssets;
    private MultiLayerNetwork net;
    private int batchSize;
    private int nEpochs;
    public SignatureSimilarityModel(List<String> allAssets, Map<String,? extends Collection<CPC>> cpcMap, CPCHierarchy hierarchy, int batchSize, int nEpochs) {
        this.hierarchy=hierarchy;
        this.batchSize=batchSize;
        this.allAssets=allAssets;
        this.cpcMap=cpcMap;
        this.nEpochs=nEpochs;
    }

    public void init() {
        allAssets = new ArrayList<>(allAssets.parallelStream().filter(asset->cpcMap.containsKey(asset)).sorted().collect(Collectors.toList()));
        Random rand = new Random(69);
        Collections.shuffle(allAssets,rand);
        testAssets = new ArrayList<>();
        trainAssets = new ArrayList<>();
        smallTestSet = new ArrayList<>();
        System.out.println("Splitting test and train");
        allAssets.forEach(asset->{
            if(rand.nextBoolean()&&rand.nextBoolean()) {
                testAssets.add(asset);
            } else {
                trainAssets.add(asset);
            }
        });
        smallTestSet.addAll(testAssets.subList(0,5000));
        System.out.println("Finished splitting test and train.");
    }

    private CPCDataSetIterator getIterator(List<String> assets, Map<String,Integer> cpcToIndexMap) {
        boolean shuffle = assets.equals(trainAssets);
        System.out.println("Shuffling? "+shuffle);
        return new CPCDataSetIterator(assets,shuffle,batchSize,cpcMap,hierarchy,cpcToIndexMap);
    }



    public void train() {
        Map<String,Integer> cpcToIdxMap;;
        {
            AtomicInteger idx = new AtomicInteger(0);
            cpcToIdxMap = hierarchy.getLabelToCPCMap().entrySet().parallelStream().filter(e -> e.getValue().getNumParts() <= MAX_CPC_DEPTH).collect(Collectors.toMap(e -> e.getKey(), e -> idx.getAndIncrement()));
            System.out.println("Input size: " + cpcToIdxMap.size());
        }
        CPCDataSetIterator trainIter = getIterator(trainAssets,cpcToIdxMap);
        int numInputs = trainIter.inputColumns();

        //Neural net configuration
        int[] hiddenLayerArray = new int[]{
                numInputs,
                (VECTOR_SIZE+numInputs)/2,
                (VECTOR_SIZE+numInputs)/2,
                VECTOR_SIZE
        };
        int rngSeed = 69;
        Nd4j.getRandom().setSeed(rngSeed);
        NeuralNetConfiguration.ListBuilder listBuilder = new NeuralNetConfiguration.Builder()
                .seed(rngSeed)
                .learningRate(0.0025)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(Updater.NESTEROVS)
                .momentum(0.8)
                .weightInit(WeightInit.XAVIER)
                .regularization(true).l2(1e-4)
                .list();
        // intput layer
        listBuilder = listBuilder.layer(0, new RBM.Builder(RBM.HiddenUnit.BINARY, RBM.VisibleUnit.BINARY)
                .activation(Activation.SIGMOID)
                .lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE)
                .nIn(numInputs)
                .nOut(hiddenLayerArray[0])
                .build());
        // encoder
        for(int i = 0; i < hiddenLayerArray.length-1; i++) {
            listBuilder = listBuilder.layer(1+i, new RBM.Builder(RBM.HiddenUnit.BINARY, RBM.VisibleUnit.BINARY)
                    .activation(Activation.SIGMOID)
                    .lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE)
                    .nIn(hiddenLayerArray[i])
                    .nOut(hiddenLayerArray[i + 1])
                    .build());
        }
        // decoder
        for(int i = hiddenLayerArray.length-1; i > 0; i--) {
            listBuilder = listBuilder.layer(hiddenLayerArray.length+(hiddenLayerArray.length-i-1), new RBM.Builder(RBM.HiddenUnit.BINARY, RBM.VisibleUnit.BINARY)
                    .activation(Activation.SIGMOID)
                    .lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE)
                    .nIn(hiddenLayerArray[i])
                    .nOut(hiddenLayerArray[i - 1])
                    .build());
        }
        // output layer
        listBuilder = listBuilder.layer(2*hiddenLayerArray.length-1, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                .activation(Activation.SIGMOID)
                .nIn(numInputs)
                .nOut(numInputs)
                .build());

        MultiLayerConfiguration conf = listBuilder
                .pretrain(true).backprop(true).build();

        net = new MultiLayerNetwork(conf);
        net.init();

        // train
        int printIterations = 1000;
        List<Double> movingAverage = new ArrayList<>();
        final int averagePeriod = 10;
        AtomicReference<Double> startingAverageError = new AtomicReference<>(null);
        AtomicReference<Double> smallestedAverage = new AtomicReference<>(null);
        AtomicReference<Integer> smallestedAverageEpoch = new AtomicReference<>(null);
        AtomicInteger iterationCount = new AtomicInteger(0);
        net.setListeners(new ScoreIterationListener(printIterations) {
            public void iterationDone(Model model, int iteration) {
                super.iterationDone(model,iteration);
                if(iterationCount.get() % (printIterations/10) == (printIterations/10)-1) {
                    System.out.print("-");
                }
                if (iterationCount.getAndIncrement() % printIterations == printIterations-1) {
                    System.out.print("Testing...");
                    double error = test(getIterator(smallTestSet,cpcToIdxMap),net);
                    System.out.println(" Error: "+error);
                    movingAverage.add(error);
                    if(movingAverage.size()==averagePeriod) {
                        double averageError = movingAverage.stream().mapToDouble((d -> d)).average().getAsDouble();
                        if(startingAverageError.get()==null) {
                            startingAverageError.set(averageError);
                        }
                        if(smallestedAverage.get()==null||smallestedAverage.get()>averageError) {
                            smallestedAverage.set(averageError);
                            smallestedAverageEpoch.set(iterationCount.get());
                        }
                        System.out.println("Sampling Test Error (Iteration "+iterationCount.get()+"): "+error);
                        System.out.println("Original Average Error: " + startingAverageError.get());
                        System.out.println("Smallest Average Error (Iteration "+smallestedAverageEpoch.get()+"): " + smallestedAverage.get());
                        System.out.println("Current Average Error: " + averageError);
                        while(movingAverage.size()>averagePeriod/2) {
                            movingAverage.remove(0);
                        }
                    }
                }
            }
        });


        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            //net.fit(trainIter);
            net.fit(trainIter);
            trainIter.reset();
            System.out.println("Testing overall model: EPOCH "+i);
            double finalTestError = test(getIterator(testAssets,cpcToIdxMap),net);
            System.out.println("Final Overall Model Error: "+finalTestError);
            System.out.println("Original Model Error: "+startingAverageError.get());
        }
    }

    private double test(DataSetIterator dataStream, MultiLayerNetwork model) {
        AtomicDouble testError = new AtomicDouble(0d);
        AtomicInteger cnt = new AtomicInteger(0);
        while(dataStream.hasNext()) {
            DataSet test = dataStream.next();
            INDArray testInput = test.getFeatures();
            INDArray testOutput = model.activate(testInput,false);
            for(int i = 0; i < batchSize; i++) {
                double sim = Transforms.cosineSim(testInput.getRow(i), testOutput.getRow(i));
                if(Double.isNaN(sim)) sim = -1d;
                testError.addAndGet(1d-sim);
                cnt.getAndIncrement();
            }
        }
       return testError.get()/cnt.get();
    }

    public void save() throws IOException {
        if(net!=null) {
            ModelSerializer.writeModel(net,networkFile,true);
        }
    }

    public static void main(String[] args) throws Exception {
        int batchSize = 5;
        int nEpochs = 5;

        Map<String,Set<String>> patentToCPCStringMap = new HashMap<>();
        patentToCPCStringMap.putAll(new AssetToCPCMap().getApplicationDataMap());
        patentToCPCStringMap.putAll(new AssetToCPCMap().getPatentDataMap());
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();
        Map<String,Set<CPC>> cpcMap = patentToCPCStringMap.entrySet().parallelStream()
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(label-> hierarchy.getLabelToCPCMap().get(ClassCodeHandler.convertToLabelFormat(label)))
                        .filter(cpc->cpc!=null).collect(Collectors.toSet())));
        cpcMap = cpcMap.entrySet().parallelStream().filter(e->e.getValue().size()>0).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

        List<String> allAssets = new ArrayList<>(cpcMap.keySet());
        SignatureSimilarityModel model = new SignatureSimilarityModel(allAssets,cpcMap,hierarchy,batchSize,nEpochs);
        model.init();
        model.train();
        model.save();
    }
}
