package models.similarity_models.combined_similarity_model;

import data_pipeline.helpers.CombinedModel;
import data_pipeline.helpers.Function2;
import data_pipeline.models.CombinedNeuralNetworkPredictionModel;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.optimize.nn_optimization.NNOptimizer;
import data_pipeline.optimize.nn_optimization.NNRefactorer;
import models.NDArrayHelper;
import models.similarity_models.cpc_encoding_model.CPCVariationalAutoEncoderNN;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.variational.VariationalAutoencoder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Evan on 12/24/2017.
 */
public class CombinedSimilarityModel extends CombinedNeuralNetworkPredictionModel<INDArray> {
    public static final String WORD_CPC_2_VEC = "wordCpc2Vec";
    public static final String CPC_VEC_NET = "cpcVecNet";
    public static final String ENCODING_VAE = "encodingVAE";
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER + "combined_similarity_model_data");
    public static final Function2<INDArray,INDArray,INDArray> AVERAGE_LABEL_FUNCTION = (f1,f2) -> {
        INDArray n1 = f1.divColumnVector(f1.norm2(1));
        INDArray n2 = f2.divColumnVector(f2.norm2(1));
        return n1.addi(Nd4j.hstack(IntStream.range(0,f1.columns()/f2.columns()).mapToObj(i->n2).collect(Collectors.toList())));
    };
    public static final Function2<INDArray,INDArray,INDArray> DEFAULT_LABEL_FUNCTION = (f1,f2) -> Nd4j.hstack(f1,f2);

    private CombinedSimilarityPipelineManager pipelineManager;
    public CombinedSimilarityModel(CombinedSimilarityPipelineManager pipelineManager, String modelName) {
        super(modelName);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        return null;
    }

    @Override
    public void train(int nEpochs) {
        MultiLayerNetwork wordCpc2Vec;
        MultiLayerNetwork cpcVecNet;
        MultiLayerNetwork encodingVae;
        int hiddenLayerSize = 128;
        int encodingSize = 128;
        int input1 = 32;
        int input2 = 32;
        int outputSize = input1+input2;
        int numHiddenEncodings = 6;
        int numHiddenDecodings = 6;
        boolean trainWordCpc2Vec = true;
        boolean trainCpcVecNet = true;
        boolean trainVae = false;
        boolean saveModels = true;
        Updater updater = Updater.RMSPROP;
        final int encodingIdx = 1 + numHiddenEncodings;
        if(numHiddenDecodings%2==1||numHiddenEncodings%2==1) throw new RuntimeException("Hidden encodings and decodings size must be even for batch norm to work...");
        if(net==null) {

            LossFunctions.LossFunction lossFunction = LossFunctions.LossFunction.COSINE_PROXIMITY;

            // build networks
            int i = 0;
            NeuralNetConfiguration.ListBuilder wordCPC2VecConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                    .updater(updater)
                    .learningRate(0.001)
                    .regularization(true).l2(1e-4)
                    .activation(Activation.TANH)
                    .list()
                    .layer(i, NNOptimizer.newBatchNormLayer(input1,input1).build())
                    .layer(i+1, NNOptimizer.newDenseLayer(input1,hiddenLayerSize).build());

            NeuralNetConfiguration.ListBuilder cpcVecNetConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                    .updater(updater)
                    .learningRate(0.001)
                    .regularization(true).l2(1e-4)
                    .activation(Activation.TANH)
                    .list()
                    .layer(i, NNOptimizer.newBatchNormLayer(input2,input2).build())
                    .layer(i+1, NNOptimizer.newDenseLayer(input2,hiddenLayerSize).build());

            // encoding hidden layers
            i+=2;
            int t = i;
            for(; i < t + numHiddenEncodings; i+=2) {
                org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize,hiddenLayerSize);
                org.deeplearning4j.nn.conf.layers.Layer.Builder norm = NNOptimizer.newBatchNormLayer(hiddenLayerSize,hiddenLayerSize);
                wordCPC2VecConf = wordCPC2VecConf.layer(i,norm.build()).layer(i+1,layer.build());
                cpcVecNetConf = cpcVecNetConf.layer(i,norm.build()).layer(i+1,layer.build());
            }

            /*
            // encoding
            org.deeplearning4j.nn.conf.layers.Layer.Builder encoding = NNOptimizer.newDenseLayer(hiddenLayerSize,encodingSize);
            // decoding
            org.deeplearning4j.nn.conf.layers.Layer.Builder decoding = NNOptimizer.newDenseLayer(encodingSize,hiddenLayerSize);

            wordCPC2VecConf = wordCPC2VecConf
                    .layer(i,encoding.build())
                    .layer(i+1, decoding.build());
            cpcVecNetConf = cpcVecNetConf
                    .layer(i,encoding.build())
                    .layer(i+1, decoding.build());


            i+=2;
            */

            // decoding hidden layers
            t = i;
            for(; i < t + numHiddenDecodings; i+=2) {
                org.deeplearning4j.nn.conf.layers.Layer.Builder layer = NNOptimizer.newDenseLayer(hiddenLayerSize,hiddenLayerSize);
                org.deeplearning4j.nn.conf.layers.Layer.Builder norm = NNOptimizer.newBatchNormLayer(hiddenLayerSize,hiddenLayerSize);
                wordCPC2VecConf = wordCPC2VecConf.layer(i,norm.build()).layer(i+1,layer.build());
                cpcVecNetConf = cpcVecNetConf.layer(i,norm.build()).layer(i+1,layer.build());
            }

            // output layers
            OutputLayer.Builder outputLayer = NNOptimizer.newOutputLayer(hiddenLayerSize,outputSize).lossFunction(lossFunction);

            wordCPC2VecConf = wordCPC2VecConf.layer(i,outputLayer.build());
            cpcVecNetConf = cpcVecNetConf.layer(i,outputLayer.build());

            wordCpc2Vec = new MultiLayerNetwork(wordCPC2VecConf.build());
            cpcVecNet = new MultiLayerNetwork(cpcVecNetConf.build());

            wordCpc2Vec.init();
            cpcVecNet.init();

            NeuralNetConfiguration.ListBuilder encodingVaeConf = new NeuralNetConfiguration.Builder(NNOptimizer.defaultNetworkConfig())
                    .learningRate(0.001)
                    .weightInit(WeightInit.XAVIER)
                    .updater(Updater.RMSPROP)
                    .regularization(true).l2(1e-4)
                    .list()
                    .layer(0, new VariationalAutoencoder.Builder()
                            .activation(Activation.TANH)
                            .nIn(encodingSize)
                            .nOut(32)
                            .encoderLayerSizes(128,128)
                            .decoderLayerSizes(128,128)
                            .pzxActivationFunction(Activation.IDENTITY)
                            .lossFunction(Activation.IDENTITY, lossFunction)
                            .build()
                    )
                    .backprop(false).pretrain(true);
            encodingVae = new MultiLayerNetwork(encodingVaeConf.build());
            encodingVae.init();

            //syncParams(wordCpc2Vec,cpcVecNet,encodingIdx);

            Map<String,MultiLayerNetwork> nameToNetworkMap = Collections.synchronizedMap(new HashMap<>());
            nameToNetworkMap.put(WORD_CPC_2_VEC,wordCpc2Vec);
            nameToNetworkMap.put(CPC_VEC_NET,cpcVecNet);
            encodingVae = null;
            this.net = new CombinedModel(nameToNetworkMap);
        } else {
            double newLearningRate = 0.0001;
            wordCpc2Vec = NNRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(WORD_CPC_2_VEC),newLearningRate,false);
            cpcVecNet = NNRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(CPC_VEC_NET),newLearningRate,false);
            encodingVae = NNRefactorer.updateNetworkLearningRate(net.getNameToNetworkMap().get(ENCODING_VAE),newLearningRate,false);
            net.getNameToNetworkMap().put(WORD_CPC_2_VEC,wordCpc2Vec);
            net.getNameToNetworkMap().put(CPC_VEC_NET,cpcVecNet);
            net.getNameToNetworkMap().put(ENCODING_VAE, encodingVae);
        }

        Function<Void,Double> trainErrorFunction = (v) -> {
            return 0d;
        };

        Function2<LocalDateTime,Double,Void> saveFunction = (datetime, score) -> {
            try {
                if(saveModels) {
                    save(datetime,score);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        final int printIterations = 500;
        final AtomicBoolean stoppingCondition = new AtomicBoolean(false);

        System.gc();
        System.gc();

        DataSetIterator dataSetIterator = pipelineManager.getDatasetManager().getTrainingIterator();
        DataSetIterator validationIterator = pipelineManager.getDatasetManager().getValidationIterator();
        List<DataSet> validationDataSets = Collections.synchronizedList(new ArrayList<>());
        int valCount = 0;
        while(validationIterator.hasNext()&&valCount<20000) {
            DataSet ds = validationIterator.next();
            validationDataSets.add(ds);
            valCount+=ds.getFeatures().rows();
            //System.gc();
        }

        System.out.println("Num validation datasets: "+validationDataSets.size());

        Function<Void,Double> testErrorFunction = (v) -> {
            System.gc();
            Pair<Double,Double> results = test(wordCpc2Vec, cpcVecNet, validationDataSets.iterator());
            System.out.println(" Test Net 1: "+results.getFirst()+"\tTest Net 2: "+results.getSecond());
            return (results.getFirst()+results.getSecond())/2;
        };

        IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, stoppingCondition);
        wordCpc2Vec.setListeners(listener);

        if(encodingVae!=null) {
            org.deeplearning4j.nn.layers.variational.VariationalAutoencoder vae
                    = (org.deeplearning4j.nn.layers.variational.VariationalAutoencoder) encodingVae.getLayer(0);
            Function<Void, Double> testVaeErrorFunction = (v) -> {
                return validationDataSets.stream().mapToDouble(ds->CPCVariationalAutoEncoderNN.test(ds.getFeatureMatrix(),vae)).average().orElse(Double.MAX_VALUE);
            };
            Function2<LocalDateTime,Double,Void> saveVaeFunction = (datetime, score) -> {
                return null;
            };
            IterationListener cpcListener = new DefaultScoreListener(printIterations, testVaeErrorFunction, trainErrorFunction, saveVaeFunction, stoppingCondition);
            cpcVecNet.setListeners(cpcListener);
        }

        System.gc();
        System.gc();

        AtomicInteger totalSeenThisEpoch = new AtomicInteger(0);
        AtomicInteger totalSeen = new AtomicInteger(0);
        try {
            for (int i = 0; i < nEpochs; i++) {
                while (dataSetIterator.hasNext()) {
                   // if((gcIter++)%printIterations/10==0) System.gc();
                    DataSet ds = dataSetIterator.next();
                    train(wordCpc2Vec, cpcVecNet, encodingVae, ds.getFeatures(), ds.getLabels(),trainWordCpc2Vec,trainCpcVecNet,trainVae);
                    totalSeenThisEpoch.getAndAdd(ds.getFeatures().rows());
                    if (stoppingCondition.get()) break;
                }
                totalSeen.getAndAdd(totalSeenThisEpoch.get());
                System.out.println("Total seen this epoch: " + totalSeenThisEpoch.get());
                System.out.println("Total seen so far: " + totalSeen.get());
                if (stoppingCondition.get()) break;
                dataSetIterator.reset();
            }
            if (stoppingCondition.get()) {
                System.out.println("Stopping condition reached...");
            }
            if (!isSaved()) {
                saveFunction.apply(LocalDateTime.now(), testErrorFunction.apply(null));
            }
        } catch(StoppingConditionMetException e) {
            System.out.println("Stopping condition met: "+e.getMessage());
        }
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

    public static void syncParams(MultiLayerNetwork net1, MultiLayerNetwork net2, int layerIdx) {
        int paramsNet1KeepsStart = 0;
        int paramsNet2KeepsStart = 0;

        for (int i = 0; i < layerIdx; i++) {
            paramsNet1KeepsStart += net1.getLayer(i).numParams();
        }
        for (int i = 0; i < layerIdx; i++) {
            paramsNet2KeepsStart += net2.getLayer(i).numParams();
        }

        Layer[] layers1 = net1.getLayers();
        Layer[] layers2 = net2.getLayers();

        int paramsNet1KeepsEnd = paramsNet1KeepsStart + layers1[layerIdx].numParams();
        int paramsNet2KeepsEnd = paramsNet2KeepsStart + layers2[layerIdx].numParams();

        layers2[layerIdx] = layers1[layerIdx];
        net2.setLayers(layers2);

        INDArray net1Params = net1.params();
        INDArray net2Params = net2.params();

        INDArray paramAvg = net1Params.get(NDArrayIndex.interval(paramsNet1KeepsStart, paramsNet1KeepsEnd))
                .addi(net2Params.get(NDArrayIndex.interval(paramsNet2KeepsStart, paramsNet2KeepsEnd))).divi(2);

        net1Params.get(NDArrayIndex.interval(paramsNet1KeepsStart, paramsNet1KeepsEnd)).assign(paramAvg);
        net2Params.get(NDArrayIndex.interval(paramsNet2KeepsStart, paramsNet2KeepsEnd)).assign(paramAvg);

        net1.setParams(net1Params);
        net2.setParams(net2Params);
    }

    public static void train(MultiLayerNetwork net1, MultiLayerNetwork net2, MultiLayerNetwork vae, INDArray features1, INDArray features2, boolean train1, boolean train2, boolean train3) {
        INDArray labels = DEFAULT_LABEL_FUNCTION.apply(features1, features2);
        if(net1!=null&&train1)net1.fit(new DataSet(features1, labels));
        if(net2!=null&&train2)net2.fit(new DataSet(features2, labels));
        if(vae!=null&&train3){
            if(net1!=null) {
                INDArray net1Labels = net1.activateSelectedLayers(0,net1.getnLayers()-1,features1);
                vae.fit(new DataSet(net1Labels,labels));
            }
            if(net2!=null) {
                INDArray net2Labels = net2.activateSelectedLayers(0,net2.getnLayers()-1,features2);
                vae.fit(new DataSet(net2Labels,labels));
            }
            vae.fit(new DataSet(labels,labels));
        }
    }

    public static Pair<Double,Double> test(MultiLayerNetwork net1, MultiLayerNetwork net2, INDArray features1, INDArray features2) {
        INDArray labels = DEFAULT_LABEL_FUNCTION.apply(features1, features2);
        INDArray predictions1 = net1.activateSelectedLayers(0,net1.getnLayers()-1,features1);
        INDArray predictions2 = net2.activateSelectedLayers(0,net2.getnLayers()-1,features2);
        return new Pair<>(test(predictions1,labels),test(predictions2,labels));
    }

    public static double test(INDArray predictions, INDArray labels) {
        return 1.0 - NDArrayHelper.sumOfCosineSimByRow(predictions,labels)/predictions.rows();
    }

    public static Pair<Double,Double> test(MultiLayerNetwork net1, MultiLayerNetwork net2, Iterator<DataSet> iterator) {
        double d1 = 0;
        double d2 = 0;
        long count = 0;
        while(iterator.hasNext()) {
            DataSet ds = iterator.next();
            Pair<Double,Double> test = test(net1,net2,ds.getFeatures(),ds.getLabels());
            d1+=test.getFirst();
            d2+=test.getSecond();
            count++;
        }
        if(count>0) {
            d1/=count;
            d2/=count;
        }
        return new Pair<>(d1,d2);
    }
}
