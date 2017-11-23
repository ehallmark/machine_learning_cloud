package models.similarity_models.cpc_encoding_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.models.NeuralNetworkPredictionModel;
import data_pipeline.models.TrainablePredictionModel;
import models.similarity_models.signatures.CPCDataSetIterator;
import models.NDArrayHelper;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.variational.BernoulliReconstructionDistribution;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import data_pipeline.helpers.Function2;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/26/17.
 */
public class CPCVariationalAutoEncoderNN extends NeuralNetworkPredictionModel<INDArray> {
    public static final int VECTOR_SIZE = 32;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"cpc_deep_vae_nn_model_data");
    private Map<String,Integer> cpcToIdxMap;
    private CPCVAEPipelineManager pipelineManager;
    private int maxCPCDepth;
    public CPCVariationalAutoEncoderNN(CPCVAEPipelineManager pipelineManager, String modelName, int maxCpcDepth) {
        super(modelName);
        this.pipelineManager= pipelineManager;
        this.maxCPCDepth=maxCpcDepth;
    }

    public Map<String,Integer> getCpcToIdxMap() {
        if(cpcToIdxMap==null) {
            RecursiveTask<CPCHierarchy> hierarchyTask = new RecursiveTask<CPCHierarchy>() {
                @Override
                protected CPCHierarchy compute() {
                    return pipelineManager.getHierarchy();
                }
            };
            cpcToIdxMap = CPCIndexMap.loadOrCreateMapForDepth(hierarchyTask,maxCPCDepth);
        }
        return cpcToIdxMap;
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    @Override
    public Map<String,INDArray> predict(List<String> assets, List<String> assignees) {
        return encode(assets,assignees,pipelineManager.getCPCMap(),pipelineManager.getBatchSize());
    }

    public Map<String,INDArray> encode(List<String> assets, List<String> assignees, Map<String, ? extends Collection<CPC>> cpcMap, int batchSize) {
        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);
        assets = assets.stream().filter(asset->cpcMap.containsKey(asset)).collect(Collectors.toList());
        CPCDataSetIterator iterator = new CPCDataSetIterator(assets,false,batchSize,cpcMap,getCpcToIdxMap());
        AtomicInteger idx = new AtomicInteger(0);
        Map<String,INDArray> assetToEncodingMap = Collections.synchronizedMap(new HashMap<>());
        while(iterator.hasNext()) {
            DataSet ds = iterator.next();
            INDArray encoding = vae.activate(ds.getFeatureMatrix(),false);
            for(int i = 0; i < encoding.rows() && idx.get()<assets.size(); i++) {
                INDArray vector = encoding.getRow(i);
                assetToEncodingMap.put(assets.get(idx.getAndIncrement()), vector);
                if(idx.get()%10000==9999)System.out.println(idx.get());
            }
        }
        // assignees
        AtomicInteger cnt = new AtomicInteger(0);
        assignees.parallelStream().forEach(assignee->{
            List<INDArray> vectors = new ArrayList<>(Stream.of(
                    Database.selectPatentNumbersFromExactAssignee(assignee),
                    Database.selectApplicationNumbersFromExactAssignee(assignee)
            ).flatMap(portfolio->portfolio.stream()).map(asset->{
                return assetToEncodingMap.get(asset);
            }).filter(vec->vec!=null).collect(Collectors.toList()));

            if(vectors.isEmpty()) return;

            if(vectors.size()>1000) {
                Collections.shuffle(vectors);
                vectors = vectors.subList(0,1000);
            }

            INDArray assigneeVec = Nd4j.vstack(vectors).mean(0);
            assetToEncodingMap.put(assignee, assigneeVec);
            if (cnt.getAndIncrement() % 10000 == 9999) {
                System.out.println("Vectorized " + cnt.get() + " assignees.");
            }
        });
        return assetToEncodingMap;
    };


    public void train(int nEpochs) {
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        DataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();
        final int numInputs = getCpcToIdxMap().size();
        final int printIterations = 100;

        if(net==null) {
            //Neural net configuration
            int[] hiddenLayerEncoder = new int[]{
                    1024,
                    1024
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
                    .learningRate(0.05)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(Updater.RMSPROP).rmsDecay(0.95)
                    //.updater(Updater.ADAM)
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

        System.out.println("Building validation matrix...");
        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        int cnt = 0;
        List<INDArray> partialValidationMatrices = new ArrayList<>();
        while(cnt<10000&&validationIterator.hasNext()) {
            INDArray features = validationIterator.next().getFeatures();
            partialValidationMatrices.add(features);
            cnt+=features.rows();
        }
        INDArray validationMatrix = Nd4j.vstack(partialValidationMatrices);
        Function<Void,Double> testErrorFunction = (v) -> {
            return test(validationMatrix, vae);
        };

        Function<Void,Double> trainErrorFunction = (v) -> {
            return 0d;//test(pipelineManager.getDatasetManager().getTrainingIterator(10000/pipelineManager.getBatchSize()), vae);
        };

        Function2<LocalDateTime,Double,Void> saveFunction = (datetime,score) -> {
            try {
                save(datetime,score);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, stoppingCondition);
        net.setListeners(listener);

        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            try {
                net.fit(trainIter);
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            if(stoppingCondition.get()) {
                break;
            }
            trainIter.reset();
        }
    }

    private double test(INDArray inputs, org.deeplearning4j.nn.layers.variational.VariationalAutoencoder model) {
        INDArray latentValues = model.activate(inputs,false);
        INDArray outputs = model.generateAtMeanGivenZ(latentValues);
        double similarity = NDArrayHelper.sumOfCosineSimByRow(inputs,outputs);
        return 1d - (similarity/inputs.rows());
    }

}
