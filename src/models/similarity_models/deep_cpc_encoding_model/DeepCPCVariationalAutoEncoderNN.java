package models.similarity_models.deep_cpc_encoding_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.helpers.Function2;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import models.similarity_models.cpc_encoding_model.CPCDataSetIterator;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.LearningRatePolicy;
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
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 10/26/17.
 */
public class DeepCPCVariationalAutoEncoderNN extends CPCVariationalAutoEncoderNN {
    public static final int VECTOR_SIZE = 32;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"deep_cpc_deep_vae_nn_model_data");

    public DeepCPCVariationalAutoEncoderNN(DeepCPCVAEPipelineManager pipelineManager, String modelName, int maxCpcDepth) {
        super(pipelineManager,modelName,maxCpcDepth);
    }

    @Override
    public Map<String,Integer> getCpcToIdxMap() {
        if(cpcToIdxMap==null) {
            RecursiveTask<CPCHierarchy> hierarchyTask = new RecursiveTask<CPCHierarchy>() {
                @Override
                protected CPCHierarchy compute() {
                    return pipelineManager.getHierarchy();
                }
            };
            cpcToIdxMap = DeepCPCIndexMap.loadOrCreateMapForDepth(hierarchyTask,maxCPCDepth);
        }
        return cpcToIdxMap;
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    public synchronized INDArray encode(List<String> assets) {
        CPCDataSetIterator iterator = new CPCDataSetIterator(assets,false,assets.size(),pipelineManager.getCPCMap(),getCpcToIdxMap());
        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);
        return vae.activate(iterator.next().getFeatureMatrix(),false);
    }


    public synchronized Pair<List<String>,INDArray> encodeCPCs(List<String> cpcs) {
        if(predictions==null) predictions = pipelineManager.loadPredictions();
        List<String> valid = new ArrayList<>(cpcs.size());
        List<INDArray> vectors = cpcs.stream().map(cpc->{
            CPC c = pipelineManager.getHierarchy().getLabelToCPCMap().get(cpc);
            while(c!=null&&!predictions.containsKey(c.getName())) {
                c = c.getParent();
            }
            if(c==null)return null;
            valid.add(cpc);
            return predictions.get(c.getName());
        }).collect(Collectors.toList());
        if(vectors.isEmpty()) return null;
        return new Pair<>(valid,Nd4j.vstack(vectors));
    }

    private static final Map<String,Collection<CPC>> ancestorsCache = Collections.synchronizedMap(new HashMap<>());
    public synchronized INDArray encodeCPCsMultiple(List<List<String>> cpcsList) {
        if(cpcToIdxMap==null) cpcToIdxMap = getCpcToIdxMap();
        CPCHierarchy hierarchy = pipelineManager.getHierarchy();

        double[][] vec = new double[cpcsList.size()][];
        for(int i = 0; i < cpcsList.size(); i++) {
            List<String> cpcs = cpcsList.get(i);
            double[] v = new double[cpcToIdxMap.size()];
            vec[i] = v;
            for(int j = 0; j < cpcs.size(); j++) {
                String cpc = cpcs.get(j);
                Collection<CPC> family = ancestorsCache.getOrDefault(cpc,hierarchy.cpcWithAncestors(cpc));
                ancestorsCache.putIfAbsent(cpc,family);

                family.forEach(member -> {
                    if (cpcToIdxMap.containsKey(member.getName())) {
                        v[cpcToIdxMap.get(member.getName())] = 1d;
                    }
                });
            }
        }

        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);
        return vae.activate(Nd4j.create(vec),false);
    }

    @Override
    public Map<String,INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        predictions = pipelineManager.loadPredictions();
        // get filings
        Map<String,Collection<CPC>> cpcMap = getCPCMap();
        List<String> filings = assets.parallelStream().map(asset->new AssetToFilingMap().getApplicationDataMap().getOrDefault(asset,new AssetToFilingMap().getPatentDataMap().getOrDefault(asset,asset)))
                .filter(cpcMap::containsKey)
                .collect(Collectors.toList());
        predictions = encodeVAE(Collections.synchronizedList(filings),assignees,classCodes,predictions,cpcMap,getCpcToIdxMap(),pipelineManager.getHierarchy(),getNet(),pipelineManager.getBatchSize());
        return predictions;
    }


    public synchronized Map<String,Collection<CPC>> getCPCMap() {
        return pipelineManager.getCPCMap();
    }



    private MultiLayerConfiguration getConf(double learningRate, int nEpochs) {
        int numInputs = getCpcToIdxMap().size();

        //Neural net configuration
        int[] hiddenLayerEncoder;
        if(modelName.equals("deep32_cpc_autoencoder")) {
            hiddenLayerEncoder = new int[]{
                    2048,
                    2048
            };
        } else {
            hiddenLayerEncoder = new int[]{
                    1024,
                    512,
                    512,
                    256,
                    256
            };

        }
        int[] hiddenLayerDecoder = new int[hiddenLayerEncoder.length];
        for(int i = 0; i < hiddenLayerEncoder.length; i++) {
            hiddenLayerDecoder[i] = hiddenLayerEncoder[hiddenLayerEncoder.length-1-i];
        }
        int rngSeed = 69;
        Activation activation = Activation.TANH;
        Nd4j.getRandom().setSeed(rngSeed);

        int numExamples = 4000000;
        int stepsPerEpoch = 1;
        int batchSize = ((DeepCPCVAEPipelineManager)pipelineManager).getMiniBatchSize();

        return new NeuralNetConfiguration.Builder()
                .seed(rngSeed)
                .learningRate(learningRate)
                .learningRateDecayPolicy(LearningRatePolicy.Schedule)
                .learningRateSchedule(createSchedule(learningRate,nEpochs,batchSize,numExamples,stepsPerEpoch))
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(Updater.RMSPROP)
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
    }

    @Override
    public void train(int nEpochs) {
        AtomicBoolean stoppingCondition = new AtomicBoolean(false);
        DataSetIterator trainIter = pipelineManager.getDatasetManager().getTrainingIterator();
        final int printIterations = 200;

        if(net==null) {
            final double learningRate = 0.05;
            net = new MultiLayerNetwork(getConf(learningRate,nEpochs));
            net.init();
        } else {
            final double learningRate =  0.002;
            INDArray params = net.params();
            net = new MultiLayerNetwork(getConf(learningRate,nEpochs));
            net.init(params,false);
            System.out.println("new learning rates: ");
            net.getLayerWiseConfigurations().getConfs().forEach((conf)->{
                conf.getLearningRateByParam().entrySet().forEach(e->{
                    System.out.println("  "+e.getKey()+": "+e.getValue());
                });
            });
        }

        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);

        System.out.println("Building validation matrix...");
        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        Function<Object,Double> testErrorFunction = (v) -> {
            double total = 0d;
            int count = 0;
            while(validationIterator.hasNext()) {
                INDArray features = validationIterator.next().getFeatures();
                double score = test(features, vae);
                count++;
                total+=score;
            }
            validationIterator.reset();
            return total/count;
        };

        Function<Object,Double> trainErrorFunction = (v) -> {
            return 0d;//test(pipelineManager.getDatasetManager().getTrainingIterator(10000/pipelineManager.getBatchSize()), vae);
        };

        System.out.println("Initial test: "+testErrorFunction.apply(net));

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

        AtomicInteger gcIter = new AtomicInteger(0);
        for (int i = 0; i < nEpochs; i++) {
            System.out.println("Starting epoch {"+(i+1)+"} of {"+nEpochs+"}");
            try {
                net.fit(trainIter);
                /*
                while(trainIter.hasNext()) {
                    DataSet ds = trainIter.next();
                    net.fit(ds);
                   // if(gcIter.getAndIncrement()%100==0)System.gc();
                }*/
            } catch(StoppingConditionMetException s) {
                System.out.println("Stopping condition met");
            }
            if(stoppingCondition.get()) {
                break;
            }
            trainIter.reset();
        }
    }



}
