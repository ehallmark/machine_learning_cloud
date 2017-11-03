package models.similarity_models.signatures;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import lombok.Getter;
import lombok.Setter;
import models.similarity_models.signatures.scorers.DefaultScoreListener;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/26/17.
 */
public class SignatureSimilarityModel implements Serializable  {
    private static final long serialVersionUID = 1L;
    public static final int VECTOR_SIZE = 32;
    public static final int MAX_CPC_DEPTH = 4;
    public static final File networkFile = new File(Constants.DATA_FOLDER+"signature_tanh_neural_network.jobj");

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
    private transient AtomicBoolean isSaved;
    private SignatureSimilarityModel(CPCHierarchy hierarchy, int batchSize, int nEpochs) {
        this.batchSize=batchSize;
        this.hierarchy=hierarchy;
        this.isSaved=new AtomicBoolean(false);
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
            //double[] norms = encoding.norm2(1).data().asDouble();
            /*
            if(probabilityVectors.max(1).gt(1).sumNumber().doubleValue() > 0d) {
                throw new RuntimeException("ERROR WITH PROBABILITY VECTOR (> 1): "+probabilityVectors.toString());
            }
            if(probabilityVectors.min(1).lt(0).sumNumber().doubleValue() > 0d) {
                throw new RuntimeException("ERROR WITH PROBABILITY VECTOR (< 0): "+probabilityVectors.toString());
            }*/
            for(int i = 0; i < encoding.rows() && idx.get()<assets.size(); i++) {
                INDArray vector = encoding.getRow(i);
                assetToEncodingMap.put(assets.get(idx.getAndIncrement()), vector);
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
        boolean reshuffleTrainingAssets = cpcToIdxMap!=null;
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
        if(reshuffleTrainingAssets) {
            Collections.shuffle(trainAssets, rand);
        }
        System.out.println("Finished splitting test and train.");
        System.out.println("Num training: "+trainAssets.size());
        System.out.println("Num test: "+testAssets.size());
    }

    private CPCDataSetIterator getIterator(List<String> assets, Map<String,Integer> cpcToIndexMap, boolean test) {
        boolean shuffle = !test;
        System.out.println("Shuffling? "+shuffle);
        return new CPCDataSetIterator(assets,shuffle,test ? 1000 : batchSize,cpcMap,cpcToIndexMap);
    }

    public void train() {
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        CPCDataSetIterator trainIter = getIterator(trainAssets, cpcToIdxMap, false);
        final int numInputs = trainIter.inputColumns();
        final int printIterations = 100;

        if(net==null) {
            //Neural net configuration
            int[] hiddenLayerEncoder = new int[]{
                    800,
                    800
            };
            int[] hiddenLayerDecoder = new int[hiddenLayerEncoder.length];
            for(int i = 0; i < hiddenLayerEncoder.length; i++) {
                hiddenLayerDecoder[i] = hiddenLayerEncoder[hiddenLayerEncoder.length-1-i];
            }
            int rngSeed = 69;
            Activation activation = Activation.TANH;
            Nd4j.getRandom().setSeed(rngSeed);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(rngSeed)
                    .learningRate(0.025)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(Updater.RMSPROP).rmsDecay(0.95)
                    //.momentum(0.8)
                    .miniBatch(true)
                    .weightInit(WeightInit.XAVIER)
                    .regularization(true).l2(1e-4)
                    .list()
                    .layer(0, new VariationalAutoencoder.Builder()
                            .encoderLayerSizes(hiddenLayerEncoder)
                            .decoderLayerSizes(hiddenLayerDecoder)
                            //.lossFunction(LossFunctions.LossFunction.KL_DIVERGENCE)
                            .activation(activation)
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
        Function<Void,Double> testFunction = (v) -> {
            return test(getIterator(smallTestSet,cpcToIdxMap, true), vae);
        };
        Function<Void,Void> saveFunction = (v) -> {
            try {
                save();
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        IterationListener listener = new DefaultScoreListener(printIterations, testFunction, saveFunction, isSaved, stoppingCondition);
        net.setListeners(listener);

        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            //net.fit(trainIter);
            try {
                net.fit(trainIter);
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            System.out.println("Testing overall model: EPOCH "+i);
            double finalTestError = test(getIterator(testAssets,cpcToIdxMap, true),vae);
            System.out.println("Final Overall Model Error: "+finalTestError);
            if(stoppingCondition.get()) {
                break;
            }
            if(!isSaved()) {
                try {
                    save();
                    // allow more saves after this
                    isSaved.set(false);
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
            trainIter.reset();
        }
    }

    private double test(DataSetIterator dataStream, org.deeplearning4j.nn.layers.variational.VariationalAutoencoder model) {
        AtomicDouble testSimilarity = new AtomicDouble(0d);
        AtomicInteger cnt = new AtomicInteger(0);
        while(dataStream.hasNext()) {
            DataSet test = dataStream.next();
            INDArray testInput = test.getFeatures();
            INDArray latentValues = model.activate(testInput,false);
            INDArray testOutput = model.generateAtMeanGivenZ(latentValues);
            double similarity = NDArrayHelper.sumOfCosineSimByRow(testInput,testOutput);
            testSimilarity.addAndGet(similarity);
            cnt.addAndGet(testInput.rows());
        }
        return 1d - (testSimilarity.get()/cnt.get());
    }



    public synchronized void save() throws IOException {
        if(net!=null) {
            isSaved.set(true);
            saveNetwork(true);
            Database.trySaveObject(this, getInstanceFile(networkFile,MAX_CPC_DEPTH));
        }
    }

    private void saveNetwork(boolean saveUpdater) throws IOException{
        ModelSerializer.writeModel(net,getModelFile(networkFile,MAX_CPC_DEPTH),saveUpdater);
    }

    public synchronized boolean isSaved() {
        return isSaved.get();
    }

    private static File getModelFile(File file, int cpcDepth) {
        return new File(file.getAbsoluteFile()+"-net-cpcdepth"+cpcDepth);
    }

    private static File getInstanceFile(File file, int cpcDepth) {
        return new File(file.getAbsoluteFile()+"-instance-cpcdepth"+cpcDepth);
    }

    public static SignatureSimilarityModel restoreAndInitModel(int cpcDepth, boolean loadUpdater) throws IOException{
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
        instance.isSaved= new AtomicBoolean(true);
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();
        instance.hierarchy = hierarchy;
        instance.init();
        return instance;
    }

    public static void main(String[] args) throws Exception {
        int batchSize = 32;
        int nEpochs = 5;
        File modelFile = getModelFile(networkFile,MAX_CPC_DEPTH);
        boolean loadModel = modelFile.exists();

        CPCHierarchy cpcHierarchy = new CPCHierarchy();
        cpcHierarchy.loadGraph();
        SignatureSimilarityModel model;
        if(loadModel) {
            System.out.println("Warning: Using previous model.");
            model = restoreAndInitModel(MAX_CPC_DEPTH,true);
        } else {
            model = new SignatureSimilarityModel(cpcHierarchy,batchSize,nEpochs);
            model.init();
        }
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
