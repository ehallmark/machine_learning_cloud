package models.similarity_models.cpc2vec_model;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import data_pipeline.models.WordVectorPredictionModel;
import models.dl4j_neural_nets.listeners.CustomWordVectorListener;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.embeddings.learning.impl.elements.CBOW;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.tokenization.tokenizer.TokenPreProcess;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/21/17.
 */
public class CPC2VecModel extends WordVectorPredictionModel<INDArray> {
    public static final int VECTOR_SIZE = 32;
    private static final int BATCH_SIZE = 512;
    private static Type MODEL_TYPE = Type.Word2Vec;
    public static final File BASE_DIR = new File(Constants.DATA_FOLDER+"cpc2vec_model_data");

    private CPC2VecPipelineManager pipelineManager;
    public CPC2VecModel(CPC2VecPipelineManager pipelineManager, String modelName) {
        super(modelName+MODEL_TYPE,MODEL_TYPE);
        this.pipelineManager=pipelineManager;
    }

    @Override
    public Map<String, INDArray> predict(List<String> assets, List<String> assignees, List<String> classCodes) {
        Map<String,INDArray> predictions = Collections.synchronizedMap(new HashMap<>());

        WeightLookupTable<VocabWord> lookupTable = net.lookupTable();

        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger found = new AtomicInteger(0);
        classCodes.forEach(cpc->{
            INDArray vec = lookupTable.vector(cpc);
            if(vec!=null) {
                found.getAndIncrement();
                predictions.put(cpc, vec);
            }
            if(total.getAndIncrement()%10000==9999) {
                System.out.println("Finished "+found.get()+" from "+total.get()+" / "+classCodes.size());
            }
        });

        Map<String,Collection<CPC>> cpcMap = pipelineManager.getCPCMap();

        assets.forEach(asset->{
            Collection<CPC> cpcs = cpcMap.get(asset);
            if(cpcs!=null) {
                List<Pair<INDArray,Double>> vectorsWithWeights = cpcs.stream()
                        .filter(cpc->predictions.containsKey(cpc.getName()))
                        .map(cpc->new Pair<>(predictions.get(cpc.getName()),Math.exp(cpc.getNumParts())))
                        .collect(Collectors.toList());
                AtomicDouble sum = new AtomicDouble(0d);
                List<INDArray> weightedVectors = vectorsWithWeights.stream().map(pair->{
                    sum.getAndAdd(pair.getSecond());
                    return pair.getFirst().mul(pair.getSecond());
                }).collect(Collectors.toList());
                if(sum.get()>0d) {
                    INDArray vec = Nd4j.vstack(weightedVectors).divi(sum.get());
                    predictions.put(asset,vec);
                }
            }
        });

        Random rand = new Random(352);
        int maxSample = 500;
        assignees.forEach(assignee->{
            List<String> assigneeAssets = Stream.of(
                    Database.selectPatentNumbersFromExactAssignee(assignee),
                    Database.selectApplicationNumbersFromAssignee(assignee)
            ).flatMap(collection->collection.stream())
                    .filter(asset->predictions.containsKey(asset))
                    .collect(Collectors.toCollection(ArrayList::new));
            // sample
            int nSamples = Math.min(maxSample,assigneeAssets.size());
            if(nSamples > 0) {
                INDArray vec = Nd4j.vstack(IntStream.range(0, nSamples).mapToObj(i -> {
                    String asset = assigneeAssets.remove(rand.nextInt(assigneeAssets.size()));
                    return predictions.get(asset);
                }).collect(Collectors.toList())).div(nSamples);
                predictions.put(assignee,vec);
            }
        });

        return predictions;
    }

    @Override
    public void train(int nEpochs) {
        Collection<String> words = pipelineManager.getTestWords();
        CPC2VecIterator iterator = pipelineManager.getDatasetManager().getTrainingIterator();

        DefaultTokenizerFactory tf = new DefaultTokenizerFactory();
        tf.setTokenPreProcessor(new TokenPreProcess() {
            @Override
            public String preProcess(String s) {
                return s;
            }
        });

        int windowSize = 3;
        int minWordFrequency = 3;
        double negativeSampling = -1;
        double sampling = 0.0001;
        //double learningRate = 0.1;
        //double minLearningRate = 0.001;
        double learningRate = 0.01;
        double minLearningRate = 0.001;

        boolean newModel = net == null;
        Word2Vec.Builder builder = new Word2Vec.Builder()
                .seed(41)
                .batchSize(BATCH_SIZE)
                .epochs(1) // hard coded to avoid learning rate from resetting
                .windowSize(windowSize)
                .layerSize(VECTOR_SIZE)
                .sampling(sampling)
                .negativeSample(negativeSampling)
                .learningRate(learningRate)
                .minLearningRate(minLearningRate)
                .allowParallelTokenization(true)
                .useAdaGrad(true)
                .resetModel(newModel)
                .minWordFrequency(minWordFrequency)
                .tokenizerFactory(tf)
                .workers(Math.max(1,Runtime.getRuntime().availableProcessors()/2))
                .iterations(1)
                .useHierarchicSoftmax(true)
                .elementsLearningAlgorithm(new CBOW<>())
                .setVectorsListeners(Collections.singleton(new CustomWordVectorListener(null,modelName,1000000,words.toArray(new String[]{}))))
                .iterate(iterator);
        if(!newModel) {
            iterator.setRunVocab(false);
            builder = builder
                    .vocabCache(net.vocab())
                    .lookupTable(net.lookupTable());
        }

        net = builder.build();
        ((Word2Vec)net).fit();

        Function<WordVectors,Void> saveFunction = sequenceVectors->{
            System.out.println("Saving...");
            double score = 0d;
            try {
                save(LocalDateTime.now(), score, sequenceVectors);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };

        Function<Void,Void> afterEpochFunction = (v) -> {
            for (String word : words) {
                Collection<String> lst = getNet().wordsNearest(word, 10);
                System.out.println("10 CPCs closest to '" + word + "': " + lst);
            }
            saveFunction.apply(getNet());
            return null;
        };

        afterEpochFunction.apply(null);
    }

    @Override
    public File getModelBaseDirectory() {
        return BASE_DIR;
    }

}
