package models.similarity_models.cpc_encoding_model;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.PipelineManager;
import data_pipeline.vectorize.DatasetManager;
import lombok.Getter;
import lombok.Setter;
import models.similarity_models.signatures.CPCDataSetIterator;
import models.similarity_models.signatures.NDArrayHelper;
import models.similarity_models.signatures.StoppingConditionMetException;
import models.similarity_models.signatures.scorers.DefaultScoreListener;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.GradientNormalization;
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
import org.nd4j.linalg.learning.Adam;
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
public class CPCVariationalAutoEncoderNN  {
    public static final int VECTOR_SIZE = 32;
    public static final File networkFile = new File(Constants.DATA_FOLDER+"cpc_deep_vae_nn.jobj");

    @Getter
    private transient MultiLayerNetwork net;
    @Getter
    private transient Map<String,Integer> cpcToIdxMap;
    private transient AtomicBoolean isSaved;
    private transient CPCVAEPipelineManager pipelineManager;
    public CPCVariationalAutoEncoderNN() {
        this.pipelineManager= new CPCVAEPipelineManager();
        this.cpcToIdxMap = pipelineManager.getOrLoadIdxMap();
        this.isSaved=new AtomicBoolean(false);
    }

    public Map<String,INDArray> encode(List<String> assets) {
        return encode(assets,pipelineManager.getCPCMap(),pipelineManager.getBatchSize());
    }

    public Map<String,INDArray> encode(List<String> assets, Map<String, ? extends Collection<CPC>> cpcMap, int batchSize) {
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
                INDArray vector = encoding.getRow(i);
                assetToEncodingMap.put(assets.get(idx.getAndIncrement()), vector);
            }
        }
        return assetToEncodingMap;
    };


    public void train(int nEpochs) {
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        DataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();
        final int numInputs = cpcToIdxMap.size();
        final int printIterations = 100;

        if(net==null) {
            //Neural net configuration
            int[] hiddenLayerEncoder = new int[]{
                    512,
                    512
            };
            int[] hiddenLayerDecoder = new int[hiddenLayerEncoder.length];
            for(int i = 0; i < hiddenLayerEncoder.length; i++) {
                hiddenLayerDecoder[i] = hiddenLayerEncoder[hiddenLayerEncoder.length-1-i];
            }
            int rngSeed = 69;
            Activation activation = Activation.LEAKYRELU;
            Nd4j.getRandom().setSeed(rngSeed);
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(rngSeed)
                    .learningRate(0.025)
                    .optimizationAlgo(OptimizationAlgorithm.LINE_GRADIENT_DESCENT)
                    .updater(Updater.RMSPROP).rmsDecay(0.95)
                    .miniBatch(true)
                    .weightInit(WeightInit.XAVIER)
                    //.gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                    //.gradientNormalizationThreshold(1d)
                    //.regularization(true).l2(1e-4)
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

        Function<Void,Double> testErrorFunction = (v) -> {
            return test(pipelineManager.getDatasetManager().getValidationIterator(), vae);
        };

        Function<Void,Double> trainErrorFunction = (v) -> {
            return test(pipelineManager.getDatasetManager().getTrainingIterator(10000/128), vae);
        };

        Function<Void,Void> saveFunction = (v) -> {
            try {
                save();
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, isSaved, stoppingCondition);
        net.setListeners(listener);

        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            try {
                net.fit(trainIter);
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            System.out.println("Testing overall model: EPOCH "+i);
            double finalTestError = test(pipelineManager.getDatasetManager().getValidationIterator(),vae);
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
        }
    }

    private void saveNetwork(boolean saveUpdater) throws IOException{
        ModelSerializer.writeModel(net,getModelFile(networkFile,pipelineManager.getMaxCpcDepth()),saveUpdater);
    }

    public synchronized boolean isSaved() {
        return isSaved.get();
    }

    private static File getModelFile(File file, int cpcDepth) {
        return new File(file.getAbsoluteFile()+"-net-cpcdepth"+cpcDepth);
    }

    public static CPCVariationalAutoEncoderNN restoreAndInitModel(int cpcDepth, boolean loadUpdater) throws IOException{
        File modelFile = getModelFile(networkFile,cpcDepth);
        if(!modelFile.exists()) {
            throw new RuntimeException("Model file not found: "+modelFile.getAbsolutePath());
        }

        CPCVariationalAutoEncoderNN instance = new CPCVariationalAutoEncoderNN();
        instance.net = ModelSerializer.restoreMultiLayerNetwork(modelFile,loadUpdater);
        instance.isSaved = new AtomicBoolean(true);

        return instance;
    }
}
