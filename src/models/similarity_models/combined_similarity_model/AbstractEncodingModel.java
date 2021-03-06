package models.similarity_models.combined_similarity_model;

import data_pipeline.helpers.CombinedModel;
import data_pipeline.helpers.Function2;
import data_pipeline.models.CombinedNeuralNetworkPredictionModel;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.models.listeners.DefaultScoreListener;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.api.layers.IOutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.graph.vertex.VertexIndices;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * Created by Evan on 12/24/2017.
 */
public abstract class AbstractEncodingModel<T extends Model,V extends DefaultPipelineManager<MultiDataSetIterator,INDArray>> extends CombinedNeuralNetworkPredictionModel<INDArray,T> {
    private static final Random rand = new Random(2352);

    protected final V pipelineManager;
    private Class<T> clazz;
    public AbstractEncodingModel(V pipelineManager, Class<T> clazz, String modelName) {
        super(modelName);
        this.clazz=clazz;
        this.pipelineManager=pipelineManager;
    }

    protected abstract Map<String,T> buildNetworksForTraining();

    protected abstract Map<String,T> updateNetworksBeforeTraining(Map<String,T> networkMap);

    protected abstract Function<Object,Double> getTestFunction();

    protected static int sample(double[] probabilities) {
        double d = 0d;
        double target = rand.nextDouble();
        for(int i = 0; i < probabilities.length; i++) {
            d+=probabilities[i];
            if(d>=target) return i;
        }
        return probabilities.length-1;
    }
    public static INDArray sampleWordVectors(List<String> words, int samples, Word2Vec wordVectors) {
        if(wordVectors==null) throw new NullPointerException("Word vectors must be preloaded...");
        if(words==null||words.isEmpty()) return null;
        words = words.stream().filter(word->wordVectors.hasWord(word)).collect(Collectors.toList());
        if(words.isEmpty()) return null;
        final List<String> finalWords = words;
        double[] tfidfs = new double[finalWords.size()];
        for(int i = 0; i < tfidfs.length; i++) {
            tfidfs[i] = 1d/Math.sqrt(wordVectors.getVocab().docAppearedIn(finalWords.get(i)));
        }
        double sum = DoubleStream.of(tfidfs).sum();
        if(sum==0) throw new RuntimeException("Sum must be > 0.");
        for(int i = 0; i < tfidfs.length; i++) {
            tfidfs[i]/=sum;
        }
        int[] indices = new int[samples];
        for(int i = 0; i < indices.length; i++) {
            indices[i] = sample(tfidfs);
        }
        Arrays.sort(indices);
        List<String> randSamples = IntStream.of(indices).sorted().mapToObj(i -> finalWords.get(i))
                .collect(Collectors.toList());
        return wordVectors.getWordVectors(randSamples);
    }

    protected abstract Function<IterationListener,Void> setListenerFunction();

    public Map<String,T> getNetworks() {
        return net.getNameToNetworkMap();
    }

    public int printIterations() {
        return 1000;
    }

    public static double test(ComputationGraph net, MultiDataSet finalDataSet) {
        net.setLayerMaskArrays(finalDataSet.getFeaturesMaskArrays(),finalDataSet.getLabelsMaskArrays());
        double score = net.score(finalDataSet,false);
        net.clearLayerMaskArrays();
        //System.out.println("Raw score: "+score);
        int divisor;
       // if(finalDataSet.getLabels(0).shape().length<=2) {
            divisor = finalDataSet.getLabels().length;
       // } else {
       //     divisor = finalDataSet.getLabels(0).shape()[finalDataSet.getLabels(0).shape().length-1];
       // }
        return 1d + score/divisor;
    }

    protected void train(MultiDataSet ds) {
        throw new RuntimeException("Please use other train method.");
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

    public static INDArray feedForwardToVertex(ComputationGraph encoder, String vertexName, INDArray... inputs) {
        for(int i = 0; i < inputs.length; i++) {
            encoder.setInput(i, inputs[i]);
        }

        boolean excludeOutputLayers = false;
        boolean train = false;
        for(int i = 0; i < encoder.topologicalSortOrder().length; ++i) {
            org.deeplearning4j.nn.graph.vertex.GraphVertex current = encoder.getVertices()[encoder.topologicalSortOrder()[i]];
            VertexIndices[] var8;
            int var9;
            int var10;
            VertexIndices v;
            int vIdx;
            int inputNum;
            if(current.isInputVertex()) {
                VertexIndices[] var14 = current.getOutputVertices();
                INDArray var15 = encoder.getInputs()[current.getVertexIndex()];
                if(current.getVertexName().equals(vertexName)) return var15;
                var8 = var14;
                var9 = var14.length;

                for(var10 = 0; var10 < var9; ++var10) {
                    v = var8[var10];
                    vIdx = v.getVertexIndex();
                    inputNum = v.getVertexEdgeNumber();
                    encoder.getVertices()[vIdx].setInput(inputNum, var15.dup());
                }
            } else if(!excludeOutputLayers || !current.isOutputVertex() || !current.hasLayer() || !(current.getLayer() instanceof IOutputLayer)) {
                INDArray out = current.doForward(train);
                if(current.hasLayer()) {
                    if(current.getVertexName().equals(vertexName)) return out;
                }

                VertexIndices[] outputsTo = current.getOutputVertices();
                if(outputsTo != null) {
                    var8 = outputsTo;
                    var9 = outputsTo.length;

                    for(var10 = 0; var10 < var9; ++var10) {
                        v = var8[var10];
                        vIdx = v.getVertexIndex();
                        inputNum = v.getVertexEdgeNumber();
                        encoder.getVertices()[vIdx].setInput(inputNum, out);
                    }
                }
            }
        }
        return null;
    }



}
