package models.similarity_models.signatures;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import lombok.Getter;
import lombok.Setter;
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
import org.deeplearning4j.optimize.api.IterationListener;
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
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/26/17.
 */
public class SignatureSimilarityModel implements Serializable  {
    private static final long serialVersionUID = 1L;
    public static final int VECTOR_SIZE = 32;
    public static final int MAX_CPC_DEPTH = 4;
    public static final File networkFile = new File(Constants.DATA_FOLDER+"signature_neural_network.jobj");

    private transient Map<String,? extends Collection<CPC>> cpcMap;
    @Getter
    private transient List<String> allAssets;
    private transient List<String> testAssets;
    private transient List<String> smallTestSet;
    private transient List<String> trainAssets;
    private transient MultiLayerNetwork net;
    private transient CPCHierarchy hierarchy;
    @Setter
    private int batchSize;
    private int nEpochs;
    private Map<String,Integer> cpcToIdxMap;
    private boolean isSaved;
    private SignatureSimilarityModel(CPCHierarchy hierarchy, int batchSize, int nEpochs) {
        this.batchSize=batchSize;
        this.hierarchy=hierarchy;
        this.isSaved=false;
        this.nEpochs=nEpochs;
    }

    public Map<String,INDArray> encode(List<String> assets) {
        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);
        assets = assets.stream().filter(asset->cpcMap.containsKey(asset)).collect(Collectors.toList());
        CPCDataSetIterator iterator = new CPCDataSetIterator(assets,false,batchSize,cpcMap,cpcToIdxMap);
        AtomicInteger idx = new AtomicInteger(0);
        Map<String,INDArray> assetToEncodingMap = Collections.synchronizedMap(new HashMap<>());
        while(iterator.hasNext()) {
            System.out.println(idx.get());
            DataSet ds = iterator.next();
            INDArray encoding = vae.activate(ds.getFeatureMatrix(),false);
            for(int i = 0; i < encoding.rows() && idx.get()<assets.size(); i++) {
                assetToEncodingMap.put(assets.get(idx.getAndIncrement()),encoding.getRow(i));
            }
        }
        return assetToEncodingMap;
    };

    public void init() {
        Map<String,Set<String>> patentToCPCStringMap = new HashMap<>();
        patentToCPCStringMap.putAll(new AssetToCPCMap().getApplicationDataMap());
        patentToCPCStringMap.putAll(new AssetToCPCMap().getPatentDataMap());
        cpcMap = patentToCPCStringMap.entrySet().parallelStream()
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(label-> hierarchy.getLabelToCPCMap().get(ClassCodeHandler.convertToLabelFormat(label)))
                        .filter(cpc->cpc!=null)
                        .flatMap(cpc->hierarchy.cpcWithAncestors(cpc).stream())
                        .distinct()
                        .filter(cpc -> cpc.getNumParts() <= MAX_CPC_DEPTH)
                        .filter(cpc -> cpcToIdxMap==null||cpcToIdxMap.containsKey(cpc.getName()))
                        .collect(Collectors.toSet())))
                .entrySet().parallelStream()
                .filter(e->e.getValue().size()>0)
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

        if(cpcToIdxMap==null){
            System.out.println("WARNING: Reindexing CPC Codes...");
            AtomicInteger idx = new AtomicInteger(0);
            cpcToIdxMap = hierarchy.getLabelToCPCMap().entrySet().parallelStream().filter(e->e.getValue().getNumParts()<=MAX_CPC_DEPTH).collect(Collectors.toMap(e -> e.getKey(), e -> idx.getAndIncrement()));
            System.out.println("Input size: " + cpcToIdxMap.size());
        }
        allAssets = new ArrayList<>(cpcMap.keySet().parallelStream().filter(asset->cpcMap.containsKey(asset)).sorted().collect(Collectors.toList()));
        Random rand = new Random(69);
        Collections.shuffle(allAssets,rand);
        testAssets = new ArrayList<>();
        trainAssets = new ArrayList<>();
        smallTestSet = new ArrayList<>();
        System.out.println("Splitting test and train");
        allAssets.forEach(asset->{
            if(rand.nextBoolean()&&rand.nextBoolean()&&rand.nextBoolean()) {
                testAssets.add(asset);
            } else {
                trainAssets.add(asset);
            }
        });
        smallTestSet.addAll(testAssets.subList(0,20000));
        System.out.println("Finished splitting test and train.");
    }

    private CPCDataSetIterator getIterator(List<String> assets, Map<String,Integer> cpcToIndexMap) {
        boolean shuffle = assets.equals(trainAssets);
        System.out.println("Shuffling? "+shuffle);
        return new CPCDataSetIterator(assets,shuffle,batchSize,cpcMap,cpcToIndexMap);
    }

    public void train() {
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        CPCDataSetIterator trainIter = getIterator(trainAssets, cpcToIdxMap);
        int numInputs = trainIter.inputColumns();

        if(net==null) {
            //Neural net configuration
            int hiddenLayerSize = 512;
            int[] hiddenLayerArray = new int[]{
                    hiddenLayerSize,
                    hiddenLayerSize,
                    hiddenLayerSize
            };
            int rngSeed = 69;
            Nd4j.getRandom().setSeed(rngSeed);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(rngSeed)
                    .learningRate(0.01)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(Updater.RMSPROP).rmsDecay(0.95)
                    //.momentum(0.8)
                    .miniBatch(true)
                    .weightInit(WeightInit.XAVIER)
                    .regularization(true).l2(1e-4)
                    .list()
                    .layer(0, new VariationalAutoencoder.Builder()
                            .encoderLayerSizes(hiddenLayerArray)
                            .decoderLayerSizes(hiddenLayerArray)
                            //.lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE)
                            .activation(Activation.LEAKYRELU)
                            .pzxActivationFunction(Activation.IDENTITY)
                            .reconstructionDistribution(new BernoulliReconstructionDistribution(Activation.SIGMOID))
                            .nIn(numInputs)
                            .nOut(VECTOR_SIZE)
                            .build()
                    )
                    .pretrain(true).backprop(false).build();

            net = new MultiLayerNetwork(conf);
            net.init();
        }

        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);
        // train
        int printIterations = 500;
        List<Double> movingAverage = new ArrayList<>();
        final int averagePeriod = 10;
        net.setListeners(new IterationListener() {
            boolean invoked = false;
            Double previousAverageError;
            double averageError;
            int iterationCount = 0;
            Double smallestAverage;
            Integer smallestAverageEpoch;
            Double startingAverageError;
            @Override
            public boolean invoked() {
                return invoked;
            }

            @Override
            public void invoke() {
                invoked = true;
            }
            @Override
            public void iterationDone(Model model, int iteration) {
                if(iterationCount % (printIterations/10) == (printIterations/10)-1) {
                    System.out.print("-");
                }
                iterationCount++;
                if(iterationCount%10000==9999&&!isSaved()) {
                    try {
                        save();
                        isSaved=false;
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
                if (iterationCount % printIterations == printIterations-1) {
                    System.out.print("Testing...");
                    double error = test(getIterator(smallTestSet,cpcToIdxMap),vae);
                    System.out.println(" Error: "+error);
                    movingAverage.add(error);
                    if(movingAverage.size()==averagePeriod) {
                        averageError = movingAverage.stream().mapToDouble((d -> d)).average().getAsDouble();
                        if(startingAverageError==null) {
                            startingAverageError = averageError;
                        }
                        if(smallestAverage==null||smallestAverage>averageError) {
                            smallestAverage = averageError;
                            smallestAverageEpoch=iterationCount;
                        }
                        System.out.println("Sampling Test Error (Iteration "+iterationCount+"): "+error);
                        System.out.println("Original Average Error: " + startingAverageError);
                        System.out.println("Smallest Average Error (Iteration "+smallestAverageEpoch+"): " + smallestAverage);
                        System.out.println("Current Average Error: " + averageError);
                        while(movingAverage.size()>averagePeriod/2) {
                            movingAverage.remove(0);
                        }
                    }
                }
                if(previousAverageError!=null&&smallestAverage!=null) {
                    // check conditions for saving model
                    if(averageError>previousAverageError && previousAverageError.equals(smallestAverage)) {
                        System.out.println("Saving model...");
                        try {
                            save();
                        } catch(Exception e) {
                            System.out.println("Error while saving: "+e.getMessage());
                            e.printStackTrace();
                        }
                        // check stopping conditions
                        if (iterationCount * batchSize > 100000 && averageError > smallestAverage * 1.2) {
                            stoppingCondition.set(true);
                            System.out.println("Stopping condition met!!!");
                            throw new StoppingConditionMetException();
                        }
                    }
                }
                previousAverageError = averageError;
            }
        });


        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            //net.fit(trainIter);
            try {
                net.fit(trainIter);
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            System.out.println("Testing overall model: EPOCH "+i);
            double finalTestError = test(getIterator(testAssets,cpcToIdxMap),vae);
            System.out.println("Final Overall Model Error: "+finalTestError);
            if(stoppingCondition.get()) {
                break;
            }
            if(!isSaved()) {
                try {
                    save();
                    // allow more saves after this
                    isSaved=false;
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            trainIter.reset();
        }
    }

    private double test(DataSetIterator dataStream, org.deeplearning4j.nn.layers.variational.VariationalAutoencoder model) {
        AtomicDouble testError = new AtomicDouble(0d);
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger nanCnt = new AtomicInteger(0);
        while(dataStream.hasNext()) {
            DataSet test = dataStream.next();
            INDArray testInput = test.getFeatures();
            INDArray latentValues = model.activate(testInput,false);
            INDArray testOutput = model.generateAtMeanGivenZ(latentValues);
            for(int i = 0; i < testOutput.rows(); i++) {
                double sim = Transforms.cosineSim(testInput.getRow(i), testOutput.getRow(i));
                if(Double.isNaN(sim)) {
                    nanCnt.getAndIncrement();
                    sim = -1d;
                }
                testError.addAndGet(1d-sim);
                cnt.getAndIncrement();
            }
        }
        if(nanCnt.get()>0) {
            System.out.println("Num NaNs: "+nanCnt.get() + " / "+ cnt.get());
        }
        return testError.get()/cnt.get();
    }

    public synchronized void save() throws IOException {
        if(net!=null) {
            isSaved=true;
            saveNetwork(true);
            Database.trySaveObject(this, getInstanceFile(networkFile,MAX_CPC_DEPTH));
        }
    }

    private void saveNetwork(boolean saveUpdater) throws IOException{
        ModelSerializer.writeModel(net,getModelFile(networkFile,MAX_CPC_DEPTH),saveUpdater);
    }

    public synchronized boolean isSaved() {
        return isSaved;
    }

    private static File getModelFile(File file, int cpcDepth) {
        return new File(file.getAbsoluteFile()+"-net-cpcdepth"+cpcDepth);
    }

    private static File getInstanceFile(File file, int cpcDepth) {
        return new File(file.getAbsoluteFile()+"-instance-cpcdepth"+cpcDepth);
    }

    public static SignatureSimilarityModel restoreAndInitModel(int cpcDepth,boolean loadUpdater) throws IOException{
        File modelFile = getModelFile(networkFile,cpcDepth);
        File instanceFile = getInstanceFile(networkFile,cpcDepth);
        if(!modelFile.exists()) {
            throw new RuntimeException("Model file not found: "+modelFile.getAbsolutePath());
        }
        if(!instanceFile.exists()) {
            throw new RuntimeException("Instance file not found: "+instanceFile.getAbsolutePath());
        }
        SignatureSimilarityModel instance = (SignatureSimilarityModel)Database.tryLoadObject(instanceFile);
        instance.net=ModelSerializer.restoreMultiLayerNetwork(modelFile,loadUpdater);
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();
        instance.hierarchy = hierarchy;
        instance.init();
        return instance;
    }

    public static void main(String[] args) throws Exception {
        int batchSize = 64;
        int nEpochs = 5;
        File modelFile = getModelFile(networkFile,MAX_CPC_DEPTH);
        boolean loadModel = modelFile.exists();

        CPCHierarchy cpcHierarchy = new CPCHierarchy();
        cpcHierarchy.loadGraph();
        SignatureSimilarityModel model;
        if(loadModel) {
            model = restoreAndInitModel(MAX_CPC_DEPTH,true);
        } else {
            model = new SignatureSimilarityModel(cpcHierarchy,batchSize,nEpochs);
        }
        model.init();
        model.train();
        if(!model.isSaved()) {
            model.save();
        }

        // test restore model
        System.out.println("Restoring model test");
        SignatureSimilarityModel clone = restoreAndInitModel(MAX_CPC_DEPTH,true);
        List<String> assetSample = clone.smallTestSet;
        System.out.println("Testing encodings");
        Map<String,INDArray> vectorMap = clone.encode(assetSample);

        vectorMap.entrySet().forEach(e->{
            System.out.println(e.getKey()+": "+Arrays.toString(e.getValue().data().asFloat()));
        });
    }
}
