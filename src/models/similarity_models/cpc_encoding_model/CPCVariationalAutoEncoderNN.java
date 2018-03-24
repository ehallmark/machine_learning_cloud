package models.similarity_models.cpc_encoding_model;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.models.NeuralNetworkPredictionModel;
import graphical_modeling.util.Pair;
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
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 10/26/17.
 */
public class CPCVariationalAutoEncoderNN extends NeuralNetworkPredictionModel<INDArray> {
    public static final int VECTOR_SIZE = 32;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"cpc_deep_vae_nn_model_data");
    protected Map<String,Integer> cpcToIdxMap;
    protected CPCVAEPipelineManager pipelineManager;
    protected int maxCPCDepth;
    protected Map<String,INDArray> predictions;
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
    public Map<String,INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return encodeVAE(assets,assignees,classCodes,predictions,pipelineManager.getCPCMap(),getCpcToIdxMap(),pipelineManager.getHierarchy(),net,pipelineManager.getBatchSize());
    }

    public void setPredictions(Map<String,INDArray> predictions) {
        this.predictions=predictions;
    }

    public static Map<String,INDArray> encodeVAE(List<String> assets, List<String> assignees, List<String> classCodes, Map<String,INDArray> previous, Map<String,Collection<CPC>> cpcMap, Map<String,Integer> cpcToIdxMap, CPCHierarchy hierarchy, MultiLayerNetwork net, int batchSize) {
        System.out.println("Starting to encode vae...");

        org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) net.getLayer(0);
        Map<String,INDArray> assetToEncodingMap = previous==null?Collections.synchronizedMap(new HashMap<>()) : previous;

        AtomicInteger idx = new AtomicInteger(0);

        // cpcs
        idx.set(0);
        AtomicInteger noData = new AtomicInteger(0);
        Map<String,Collection<CPC>> classCodeCpcMap = Collections.synchronizedMap(new HashMap<>(classCodes.size()));
        classCodes = classCodes.stream().filter(cpc-> {
            Collection<CPC> cpcFamily = hierarchy.cpcWithAncestors(ClassCodeHandler.convertToLabelFormat(cpc)).stream().filter(c -> cpcToIdxMap.containsKey(c.getName())).collect(Collectors.toList());
            if (cpcFamily.size() > 0) {
                classCodeCpcMap.put(cpc, cpcFamily);
                return true;
            } else {
                noData.getAndIncrement();
                return false;
            }
        }).collect(Collectors.toList());
        CPCDataSetIterator classCodeIterator = new CPCDataSetIterator(classCodes,false,batchSize,classCodeCpcMap,cpcToIdxMap);
        while(classCodeIterator.hasNext()) {
            DataSet ds = classCodeIterator.next();
            INDArray encoding = vae.activate(ds.getFeatureMatrix(),false);
            encoding.diviColumnVector(encoding.norm2(1));
            for(int i = 0; i < encoding.rows() && idx.get()<classCodes.size(); i++) {
                INDArray vector = encoding.getRow(i);
                assetToEncodingMap.put(classCodes.get(idx.getAndIncrement()), vector);
                if(idx.get()%50000==49999) {
                    System.gc();
                    System.out.println(idx.get());
                }
            }
        }
        System.out.println("Total num cpc errors: "+noData.get());


        // assignees
        idx.set(0);
        final int assetLimit = 500;
        final int cpcSample = 30;
        noData.set(0);
        Random rand = new Random();
        System.out.println("Predicting assignees...");
        Map<String,Collection<CPC>> assigneeCpcMap = Collections.synchronizedMap(new HashMap<>(classCodes.size()));
        assignees = assignees.stream().filter(assignee-> {
            List<String> assigneeAssets = Stream.of(
                    Database.selectPatentNumbersFromExactAssignee(assignee),
                    Database.selectApplicationNumbersFromExactAssignee(assignee)
            ).flatMap(portfolio -> portfolio.stream()).distinct()
                    .flatMap(asset->Stream.of(
                            asset,new AssetToFilingMap().getPatentDataMap().getOrDefault(asset,new AssetToFilingMap().getApplicationDataMap().get(asset)))
                    ).filter(f->f!=null).distinct().filter(f->cpcMap.containsKey(f)).collect(Collectors.toCollection(ArrayList::new));
            List<String> assetSample = IntStream.range(0, Math.min(assigneeAssets.size(), assetLimit)).mapToObj(a -> assigneeAssets.remove(rand.nextInt(assigneeAssets.size()))).collect(Collectors.toList());
            Collection<CPC> cpcFamily = assetSample.stream().flatMap(asset->cpcMap.getOrDefault(asset,Collections.emptyList()).stream())
                    .collect(Collectors.groupingBy(asset->asset,Collectors.counting()))
                    .entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                    .limit(cpcSample)
                    .map(cpc->cpc.getKey().getName())
                    .flatMap(cpc->{
                        return hierarchy.cpcWithAncestors(ClassCodeHandler.convertToLabelFormat(cpc)).stream().filter(c -> cpcToIdxMap.containsKey(c.getName()));
                    }).collect(Collectors.toList());

            if (cpcFamily.size() > 0) {
                assigneeCpcMap.put(assignee, cpcFamily);
                return true;
            } else {
                noData.getAndIncrement();
                return false;
            }
        }).collect(Collectors.toList());
        CPCDataSetIterator assigneeIterator = new CPCDataSetIterator(assignees,false,batchSize,assigneeCpcMap,cpcToIdxMap);
        while(assigneeIterator.hasNext()) {
            DataSet ds = assigneeIterator.next();
            INDArray encoding = vae.activate(ds.getFeatureMatrix(),false);
            encoding.diviColumnVector(encoding.norm2(1));
            for(int i = 0; i < encoding.rows() && idx.get()<assignees.size(); i++) {
                INDArray vector = encoding.getRow(i);
                assetToEncodingMap.put(assignees.get(idx.getAndIncrement()), vector);
                if(idx.get()%50000==49999) {
                    System.gc();
                    System.out.println(idx.get());
                }
            }
        }
        System.out.println("Total num assignee errors: "+noData.get());

        assets = assets.stream().filter(asset->cpcMap.containsKey(asset)).collect(Collectors.toList());
        CPCDataSetIterator iterator = new CPCDataSetIterator(assets,false,batchSize,cpcMap,cpcToIdxMap);
        idx.set(0);
        while(iterator.hasNext()) {
            DataSet ds = iterator.next();
            INDArray encoding = vae.activate(ds.getFeatureMatrix(),false);
            encoding.diviColumnVector(encoding.norm2(1));
            for(int i = 0; i < encoding.rows() && idx.get()<assets.size(); i++) {
                INDArray vector = encoding.getRow(i);
                assetToEncodingMap.put(assets.get(idx.getAndIncrement()), vector);
                if(idx.get()%100000==99999) {
                    System.gc();
                    System.out.println(idx.get());
                }
            }
        }
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
        Function<Object,Double> testErrorFunction = (v) -> {
            org.nd4j.linalg.primitives.Pair<Double,Double> p = test(validationMatrix, vae);
            return (p.getFirst()+p.getSecond())/2;
        };

        Function<Object,Double> trainErrorFunction = (v) -> {
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

    public static org.nd4j.linalg.primitives.Pair<Double,Double> test(INDArray inputs, org.deeplearning4j.nn.layers.variational.VariationalAutoencoder model) {
        INDArray latentValues = model.activate(inputs,false);
        INDArray outputs = model.generateAtMeanGivenZ(latentValues);
        double s1 = outputs.mul(inputs).norm1(1).divi(inputs.sum(1)).meanNumber().doubleValue();
        INDArray negInputs = inputs.rsub(1);
        double s2 = outputs.mul(negInputs).norm1(1).divi(negInputs.sum(1)).meanNumber().doubleValue();
        return new org.nd4j.linalg.primitives.Pair<>(s1,s2);
    }

}
