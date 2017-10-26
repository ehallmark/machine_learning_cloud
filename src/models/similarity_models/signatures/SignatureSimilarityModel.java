package models.similarity_models.signatures;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/26/17.
 */
public class SignatureSimilarityModel {
    public static final int VECTOR_SIZE = 30;
    public static final int MAX_CPC_DEPTH = 2;
    public static final File networkFile = new File(Constants.DATA_FOLDER+"signature_neural_network.jobj");

    private CPCHierarchy hierarchy;
    private Map<String,? extends Collection<CPC>> cpcMap;
    private List<String> allAssets;
    private List<String> testAssets;
    private List<String> smallTestSet;
    private List<String> trainAssets;
    private MultiLayerNetwork net;
    private int numInputs;
    private Map<String,Integer> cpcToIdxMap;
    private int batchSize;
    public SignatureSimilarityModel(List<String> allAssets, Map<String,? extends Collection<CPC>> cpcMap, CPCHierarchy hierarchy, int batchSize) {
        this.hierarchy=hierarchy;
        this.batchSize=batchSize;
        this.allAssets=allAssets;
        this.cpcMap=cpcMap;
    }

    public void init() {
        allAssets = new ArrayList<>(allAssets.parallelStream().filter(asset->cpcMap.containsKey(asset)).collect(Collectors.toList()));
        Collections.shuffle(allAssets);
        Random rand = new Random(69);
        testAssets = Collections.synchronizedList(new ArrayList<>());
        trainAssets = Collections.synchronizedList(new ArrayList<>());
        smallTestSet = Collections.synchronizedList(new ArrayList<>());
        System.out.println("Splitting test and train");
        allAssets.parallelStream().forEach(asset->{
            if(rand.nextBoolean()&&rand.nextBoolean()) {
                testAssets.add(asset);
            } else {
                trainAssets.add(asset);
            }
        });
        smallTestSet.addAll(testAssets.subList(0,1000));

        {
            AtomicInteger idx = new AtomicInteger(0);
            cpcToIdxMap = hierarchy.getLabelToCPCMap().entrySet().parallelStream().filter(e -> e.getValue().getNumParts() <= MAX_CPC_DEPTH).collect(Collectors.toMap(e -> e.getKey(), e -> idx.getAndIncrement()));
            numInputs = cpcToIdxMap.size();
            System.out.println("Input size: "+numInputs);
        }
        System.out.println("Finished splitting test and train.");
    }

    private Stream<DataSet> getIterator(List<String> assets) {
        if(assets.equals(trainAssets)) {
            System.out.println("Shuffling training data...");
            Collections.shuffle(assets);
        }
        return IntStream.range(0,assets.size()/batchSize).parallel().mapToObj(i->{
            INDArray features = createVector(IntStream.range(i,i+batchSize).mapToObj(j->{
                String asset = assets.get(j);
                return cpcMap.get(asset);
            }));
            return new DataSet(features, features);
        }).sequential();
    }

    private INDArray createVector(Stream<Collection<CPC>> cpcStream) {
        INDArray matrix = Nd4j.create(batchSize,numInputs);
        AtomicInteger batch = new AtomicInteger(0);
        cpcStream.forEach(cpcs->{
            double[] vec = new double[numInputs];
            cpcs.stream().flatMap(cpc->hierarchy.cpcWithAncestors(cpc).stream()).filter(cpc->cpcToIdxMap.containsKey(cpc.getName())).forEach(cpc->{
                int idx = cpcToIdxMap.get(cpc.getName());
                vec[idx] = 1d;
            });
            INDArray a = Nd4j.create(vec);
            //Number norm2 = a.norm2Number();
            //if(norm2.doubleValue()>0) {
            //    a.divi(norm2);
            //} else {
            //    System.out.println("NO NORM!!!");
            //}
            matrix.putRow(batch.get(),a);
            batch.getAndIncrement();
        });
        return matrix;
    }

    public void train() {
        //Neural net configuration
        int rngSeed = 69;
        Nd4j.getRandom().setSeed(rngSeed);
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(rngSeed)
                .learningRate(1e-2)
                .updater(Updater.ADAGRAD)
                .weightInit(WeightInit.XAVIER)
                .regularization(true).l2(1e-4)
                .list()
                .layer(0, new VariationalAutoencoder.Builder()
                        .activation(Activation.RELU)
                        .encoderLayerSizes(100, 100)
                        .decoderLayerSizes(100, 100)
                        .pzxActivationFunction(Activation.IDENTITY)  //p(z|data) activation function
                        .reconstructionDistribution(new BernoulliReconstructionDistribution(Activation.SIGMOID.getActivationFunction()))
                        .nIn(numInputs)
                        .nOut(VECTOR_SIZE)
                        .build())
                .pretrain(true).backprop(false).build();

        net = new MultiLayerNetwork(conf);
        net.init();

        //Get the variational autoencoder layer
        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);

        // train
        int nEpochs = 10;
        //Perform training
        int printIterations = 10000;
        AtomicInteger iterationCount = new AtomicInteger(0);
        for (int i = 0; i < nEpochs; i++) {
            Stream<DataSet> trainIter = getIterator(trainAssets);
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            trainIter.forEach(ds->{
                net.fit(ds);
                if(iterationCount.get() % 1000 == 999) {
                    test(getIterator(smallTestSet),vae);
                    System.out.println("Completed: "+iterationCount.get()+ " batches.");
                }
                //Every N=100 minibatches:
                // (a) collect the test set latent space values for later plotting
                // (b) collect the reconstructions at each point in the grid
                if (iterationCount.getAndIncrement() % printIterations == printIterations-1) {

                }
            });
        }
        System.out.println("Testing overall model");
        test(getIterator(testAssets),vae);
    }

    private void test(Stream<DataSet> dataStream, org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae) {
        System.out.println("Testing...");
        AtomicDouble testError = new AtomicDouble(0d);
        AtomicInteger cnt = new AtomicInteger(0);
        dataStream.forEach(test->{
            INDArray testInput = test.getFeatures();
            INDArray latentSpace = vae.activate(testInput,false);
            INDArray testOutput = vae.generateAtMeanGivenZ(latentSpace);
            for(int i = 0; i < batchSize; i++) {
                double sim = Transforms.cosineSim(testInput.getRow(i), testOutput.getRow(i));
                if(Double.isNaN(sim)) sim = -1d;
                testError.addAndGet(1d-sim);
                cnt.getAndIncrement();
            }
        });
        System.out.println("Test Error for "+cnt.get()+" assets: "+testError.get()/cnt.get());
    }

    public void save() throws IOException {
        if(net!=null) {
            ModelSerializer.writeModel(net,networkFile,true);
        }
    }

    public static void main(String[] args) throws Exception {
        int batchSize = 20;

        Map<String,Set<String>> patentToCPCStringMap = Collections.synchronizedMap(new HashMap<>());
        patentToCPCStringMap.putAll(new AssetToCPCMap().getApplicationDataMap());
        patentToCPCStringMap.putAll(new AssetToCPCMap().getPatentDataMap());
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();
        Map<String,Set<CPC>> cpcMap = patentToCPCStringMap.entrySet().parallelStream()
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(label-> hierarchy.getLabelToCPCMap().get(ClassCodeHandler.convertToLabelFormat(label)))
                        .filter(cpc->cpc!=null).collect(Collectors.toSet())));
        cpcMap = cpcMap.entrySet().parallelStream().filter(e->e.getValue().size()>0).collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

        List<String> allAssets = new ArrayList<>(cpcMap.keySet());
        SignatureSimilarityModel model = new SignatureSimilarityModel(allAssets,cpcMap,hierarchy,batchSize);
        model.init();
        model.train();
        model.save();
    }
}
