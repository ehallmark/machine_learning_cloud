package models.similarity_models.combined_similarity_model;

import data_pipeline.helpers.CombinedModel;
import data_pipeline.helpers.Function2;
import data_pipeline.models.CombinedNeuralNetworkPredictionModel;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Evan on 12/24/2017.
 */
public abstract class AbstractCombinedSimilarityModel<T extends Model,V extends DefaultPipelineManager<MultiDataSetIterator,INDArray>> extends CombinedNeuralNetworkPredictionModel<INDArray,T> {

    public static final Function2<INDArray,INDArray,INDArray> AVERAGE_LABEL_FUNCTION = (f1,f2) -> {
        INDArray n1 = f1.divColumnVector(f1.norm2(1));
        INDArray n2 = f2.divColumnVector(f2.norm2(1));
        return n1.addi(Nd4j.hstack(IntStream.range(0,f1.columns()/f2.columns()).mapToObj(i->n2).collect(Collectors.toList())));
    };
    public static final Function2<INDArray,INDArray,INDArray> DEFAULT_LABEL_FUNCTION = (f1,f2) -> Nd4j.hstack(f1,f2);

    protected V pipelineManager;
    private Class<T> clazz;
    public AbstractCombinedSimilarityModel(V pipelineManager, Class<T> clazz, String modelName) {
        super(modelName);
        this.clazz=clazz;
        this.pipelineManager=pipelineManager;
    }

    protected abstract Map<String,T> buildNetworksForTraining();
    protected abstract Map<String,T> updateNetworksBeforeTraining(Map<String,T> networkMap);
    protected abstract Function<Object,Double> getTestFunction();
    protected abstract void train(MultiDataSet dataSet);
    protected abstract Function<IterationListener,Void> setListenerFunction();

    public Map<String,T> getNetworks() {
        return net.getNameToNetworkMap();
    }

    public int printIterations() {
        return 500;
    }

    public static double test(ComputationGraph net, MultiDataSet finalDataSet) {
        return 1d+net.score(finalDataSet)/finalDataSet.getLabels().length;
    }

    @Override
    public void train(int nEpochs) {
        if(this.net==null) {
            Map<String,T> nameToNetworkMap = buildNetworksForTraining();
            this.net = new CombinedModel<>(nameToNetworkMap, clazz);
        } else {
            Map<String,T> updates = updateNetworksBeforeTraining(this.net.getNameToNetworkMap());
            updates.forEach((name,model)->{
                this.net.getNameToNetworkMap().put(name,model);
            });
        }

        Function<Object,Double> trainErrorFunction = (v) -> {
            return 0d;
        };

        Function2<LocalDateTime,Double,Void> saveFunction = (datetime, score) -> {
            try {
                save(datetime,score);
            } catch(Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        final int printIterations = printIterations();
        final AtomicBoolean stoppingCondition = new AtomicBoolean(false);

        System.gc();
        System.gc();

        MultiDataSetIterator dataSetIterator = pipelineManager.getDatasetManager().getTrainingIterator();

        Function<Object,Double> testErrorFunction = getTestFunction();

        IterationListener listener = new DefaultScoreListener(printIterations, testErrorFunction, trainErrorFunction, saveFunction, stoppingCondition);
        setListenerFunction().apply(listener);


        System.gc();
        System.gc();

        train(dataSetIterator,nEpochs,stoppingCondition);

        if (!isSaved()) {
            saveFunction.apply(LocalDateTime.now(), testErrorFunction.apply(null));
        }
    }

    protected void train(MultiDataSetIterator dataSetIterator, int nEpochs, AtomicBoolean stoppingCondition) {
        AtomicInteger totalSeenThisEpoch = new AtomicInteger(0);
        AtomicInteger totalSeen = new AtomicInteger(0);
        try {
            for (int i = 0; i < nEpochs; i++) {
                while (dataSetIterator.hasNext()) {
                    // if((gcIter++)%printIterations/10==0) System.gc();
                    MultiDataSet ds = dataSetIterator.next();

                    train(ds);

                    totalSeenThisEpoch.getAndAdd(ds.getFeatures(0).shape()[0]);
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

        } catch(StoppingConditionMetException e) {
            System.out.println("Stopping condition met: "+e.getMessage());
        }
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

    public static void train(ComputationGraph net, MultiDataSet dataSet) {
        if(net!=null)net.fit(dataSet);
    }

    public static void train(ComputationGraph net, INDArray features, INDArray labels, INDArray featuresMask, INDArray labelsMask) {
        if(net!=null)net.fit(new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{features},new INDArray[]{labels}, new INDArray[]{featuresMask},new INDArray[]{labelsMask}));
    }

    public static Pair<Double,Double> test(MultiLayerNetwork net1, MultiLayerNetwork net2, INDArray features1, INDArray features2) {
        INDArray labels = DEFAULT_LABEL_FUNCTION.apply(features1, features2);
        return new Pair<>(1d+net1.score(new DataSet(features1,labels)),1d+net2.score(new DataSet(features2,labels)));
    }


    public static Pair<Double,Double> test(ComputationGraph net1, ComputationGraph net2, MultiDataSet dataSet1, MultiDataSet dataSet2) {
        return new Pair<>(1d+net1.score(dataSet1)/dataSet1.getLabels().length,1d+net2.score(dataSet2)/dataSet2.getLabels().length);
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
