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
    public static final File networkFile = new File(Constants.DATA_FOLDER+"signature_neural_network.jobj");

    private CPCHierarchy hierarchy;
    private Map<String,? extends Collection<CPC>> cpcMap;
    private List<String> allAssets;
    private List<String> testAssets;
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
        allAssets = allAssets.parallelStream().filter(asset->cpcMap.containsKey(asset)).collect(Collectors.toList());
        Random rand = new Random(69);
        testAssets = Collections.synchronizedList(new ArrayList<>());
        trainAssets = Collections.synchronizedList(new ArrayList<>());
        System.out.println("Splitting test and train");
        allAssets.parallelStream().forEach(asset->{
            if(rand.nextBoolean()&&rand.nextBoolean()) {
                testAssets.add(asset);
            } else {
                trainAssets.add(asset);
            }
        });
        {
            AtomicInteger idx = new AtomicInteger(0);
            cpcToIdxMap = hierarchy.getLabelToCPCMap().entrySet().parallelStream().filter(e -> e.getValue().getNumParts() < 5).collect(Collectors.toMap(e -> e.getKey(), e -> idx.getAndIncrement()));
            numInputs = cpcToIdxMap.size();
        }
        System.out.println("Finished splitting test and train.");
    }

    private Iterator<DataSet> getIterator(boolean train) {
        List<String> assets = (train ? trainAssets : testAssets);
        Collections.shuffle(assets);
        return IntStream.range(0,assets.size()/batchSize).parallel().mapToObj(i->{
            INDArray features = createVector(IntStream.range(i,i+batchSize).mapToObj(j->{
                String asset = assets.get(j);
                return cpcMap.get(asset);
            }));
            return new DataSet(features, features.dup());
        }).iterator();
    }

    private INDArray createVector(Stream<Collection<CPC>> cpcStream) {
        INDArray matrix = Nd4j.create(batchSize,numInputs);
        AtomicInteger batch = new AtomicInteger(0);
        cpcStream.forEach(cpcs->{
            double[] vec = new double[numInputs];
            cpcs.stream().flatMap(cpc->hierarchy.cpcWithAncestors(cpc).stream()).filter(cpc->cpcToIdxMap.containsKey(cpc.getName())).forEach(cpc->{
                int idx = cpcToIdxMap.get(cpc.getName());
                vec[idx]+= 1d/cpc.numSubclasses();
            });
            INDArray a = Nd4j.create(vec);
            a.divi(a.norm2Number());
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
                .updater(Updater.RMSPROP)
                .weightInit(WeightInit.XAVIER)
                .regularization(true).l2(1e-4)
                .list()
                .layer(0, new VariationalAutoencoder.Builder()
                        .activation(Activation.LEAKYRELU)
                        .encoderLayerSizes(1000, 1000)        //2 encoder layers, each of size 256
                        .decoderLayerSizes(1000, 1000)        //2 decoder layers, each of size 256
                        .pzxActivationFunction(Activation.IDENTITY)  //p(z|data) activation function
                        .reconstructionDistribution(new BernoulliReconstructionDistribution(Activation.SIGMOID.getActivationFunction()))     //Bernoulli distribution for p(data|z) (binary or 0 to 1 data only)
                        .nIn(numInputs)                       //Input size: 28x28
                        .nOut(VECTOR_SIZE)                            //Size of the latent variable space: p(z|x). 2 dimensions here for plotting, use more in general
                        .build())
                .pretrain(true).backprop(false).build();

        net = new MultiLayerNetwork(conf);
        net.init();

        //Get the variational autoencoder layer
        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);

        // train
        int nEpochs = 2;
        //Perform training
        int iterationCount = 0;
        for (int i = 0; i < nEpochs; i++) {
            Iterator<DataSet> trainIter = getIterator(true);
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            while (trainIter.hasNext()) {
                DataSet ds = trainIter.next();
                net.fit(ds);

                //Every N=100 minibatches:
                // (a) collect the test set latent space values for later plotting
                // (b) collect the reconstructions at each point in the grid
                if (iterationCount++ % 1000 == 0) {
                    Iterator<DataSet> testIter = getIterator(false);
                    AtomicDouble testError = new AtomicDouble(0d);
                    while(testIter.hasNext()) {
                        INDArray testInput = testIter.next().getFeatures();
                        INDArray latentSpace = vae.activate(testInput,false);
                        INDArray testOutput = vae.generateAtMeanGivenZ(latentSpace);
                        double error = 1d - Transforms.cosineSim(testInput,testOutput);
                        testError.addAndGet(error);
                    }
                    System.out.println("Overall test error: "+testError.get());
                }
            }
        }
    }

    public void save() throws IOException {
        if(net!=null) {
            ModelSerializer.writeModel(net,networkFile,true);
        }
    }

    public static void main(String[] args) throws Exception {
        int batchSize = 10;

        Map<String,Set<String>> patentToCPCStringMap = Collections.synchronizedMap(new HashMap<>());
        patentToCPCStringMap.putAll(new AssetToCPCMap().getApplicationDataMap());
        patentToCPCStringMap.putAll(new AssetToCPCMap().getPatentDataMap());
        CPCHierarchy hierarchy = new CPCHierarchy();
        hierarchy.loadGraph();
        Map<String,Set<CPC>> cpcMap = patentToCPCStringMap.entrySet().parallelStream()
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(label->hierarchy.getLabelToCPCMap().get(label)).filter(cpc->cpc!=null).collect(Collectors.toSet())));

        List<String> allAssets = new ArrayList<>(cpcMap.keySet());
        SignatureSimilarityModel model = new SignatureSimilarityModel(allAssets,cpcMap,hierarchy,batchSize);
        model.init();
        model.train();
        model.save();
    }
}
