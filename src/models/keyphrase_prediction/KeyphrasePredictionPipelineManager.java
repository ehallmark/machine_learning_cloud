package models.keyphrase_prediction;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.models.DefaultModel;
import models.keyphrase_prediction.models.DefaultModel2;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.*;
import models.similarity_models.combined_similarity_model.CombinedDeepCPC2VecEncodingModel;
import models.similarity_models.combined_similarity_model.CombinedDeepCPC2VecEncodingPipelineManager;
import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import tools.MinHeap;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 12/21/17.
 */
public class KeyphrasePredictionPipelineManager extends DefaultPipelineManager<WordCPCIterator,Set<String>> {
    public static final Model modelParams = new DefaultModel2();
    @Getter
    private WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    @Getter
    private CombinedDeepCPC2VecEncodingPipelineManager combinedDeepCPC2VecEncodingPipelineManager;
    private static final File INPUT_DATA_FOLDER = new File("keyphrase_prediction_input_data/");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"keyphrase_prediction_model_predictions/predictions_map.jobj");
    @Getter
    private static Map<MultiStem,INDArray> keywordToVectorLookupTable;
    private static final File keywordToVectorLookupTableFile = new File(Constants.DATA_FOLDER+"keyword_to_vector_predictions_lookup_table.jobj");
    private Set<MultiStem> multiStemSet;
    private static Map<String,Collection<CPC>> cpcMap;
    private static Map<String,MultiStem> labelToKeywordMap;
    public KeyphrasePredictionPipelineManager(WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
    }

    public KeyphrasePredictionPipelineManager(CombinedDeepCPC2VecEncodingPipelineManager combinedDeepCPC2VecEncodingPipelineManager) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.combinedDeepCPC2VecEncodingPipelineManager=combinedDeepCPC2VecEncodingPipelineManager;
    }

    @Override
    public DataSetManager<WordCPCIterator> getDatasetManager() {
        if(datasetManager==null) {
            setDatasetManager();
        }
        return datasetManager;
    }


    public Map<String,Collection<CPC>> getCPCMap() {
        if(cpcMap==null) {
            cpcMap = wordCPC2VecPipelineManager.getCPCMap();
        }
        return cpcMap;
    }

    @Override
    public void rebuildPrerequisiteData() {
        initStages(true,true,false,true);
    }

    private void initStages(boolean vocab, boolean filters, boolean rerunVocab, boolean rerunFilters) {
        // stage 1;
        Stage1 stage1 = new Stage1(modelParams);
        if(vocab)stage1.run(rerunVocab);
        //if(alwaysRerun)stage1.createVisualization();

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Stage2 stage2 = new Stage2(stage1.get(), modelParams);
        if(filters)stage2.run(rerunFilters);
        //if(alwaysRerun)stage2.createVisualization();

        // valid word
        System.out.println("Pre-grouping data for valid word stage...");
        ValidWordStage validWordStage = new ValidWordStage(stage2.get(), modelParams);
        if(filters)validWordStage.run(rerunFilters);

        // cpc density
        System.out.println("Pre-grouping data for cpc density...");
        CPCHierarchy hierarchy = new CPCHierarchy();
        CPCDensityStage cpcDensityStage = new CPCDensityStage(validWordStage.get(), modelParams, hierarchy);
        if(filters)cpcDensityStage.run(rerunFilters);
        //if(alwaysRerun) stage4.createVisualization();

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Stage3 stage3 = new Stage3(cpcDensityStage.get(), modelParams);
        if(filters) stage3.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        // stage 3
        System.out.println("Pre-grouping data for word order...");
        WordOrderStage wordOrder = new WordOrderStage(stage3.get(), stage1.get(), modelParams);
        wordOrder.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        multiStemSet = wordOrder.get();

        labelToKeywordMap = Collections.synchronizedMap(new HashMap<>());
        multiStemSet.parallelStream().forEach(stem->labelToKeywordMap.put(stem.getBestPhrase(),stem));
    }

    @Override
    protected void initModel(boolean forceRecreateModel) {
        System.out.println("Starting to init model...");
        if(multiStemSet==null) initStages(false,false,false,false);
    }


    private MultiStem findByLabel(String keyword) {
        return labelToKeywordMap.get(keyword.toLowerCase());
    }

    private MultiStem findOrCreateByLabel(String keyword) {
        MultiStem multiStem = labelToKeywordMap.get(keyword.toLowerCase());
        if(multiStem==null) {
            if(keywordToVectorLookupTable==null) {
                buildKeywordToLookupTableMap();
            }

            Map<String,INDArray> wordVectorMap = getWordCPC2VecPipelineManager().getOrLoadWordVectors();
            // create
            multiStem = new MultiStem(keyword.toLowerCase().split(" "),-1);
            multiStem.setBestPhrase(keyword.toLowerCase());
            List<INDArray> wordVectors = Stream.of(multiStem.getStems()).map(word->wordVectorMap.get(word)).filter(vec->vec!=null).collect(Collectors.toList());
            if(wordVectors.size()>=Math.max(1,multiStem.getStems().length-1)) {
                INDArray vec = Transforms.unitVec(Nd4j.vstack(wordVectors).mean(0));
                keywordToVectorLookupTable.put(multiStem, vec);
            } else {
                multiStem = null;
            }
        }
        return multiStem;
    }


    public Map<String,Set<String>> predict(Collection<String> keywords, Map<String,INDArray> toPredictMap, int maxTags, double minScore) {
        List<MultiStem> keywordStems = keywords.stream().map(keyword->findOrCreateByLabel(keyword)).filter(mul->mul!=null).collect(Collectors.toList());
        System.out.println("In key phrase manager, num keyword stems found: "+keywordStems.size()+ " out of "+keywords.size());
        return predict(keywordStems, toPredictMap, maxTags, minScore);
    }

    public Map<String,Set<String>> predict(List<MultiStem> keywords, Map<String,INDArray> toPredictMap, int maxTags, double minScore) {
        if(keywordToVectorLookupTable==null) {
            System.out.println("Loading keyword to vector table...");
            buildKeywordToLookupTableMap();
        }

        System.out.println("Building keyword vector pairs...");
        INDArray keywordMatrix = Nd4j.create(keywords.size(),keywordToVectorLookupTable.values().stream().findAny().get().length());
        for(int i = 0; i < keywords.size(); i++) {
            INDArray vec = keywordToVectorLookupTable.get(keywords.get(i));
            keywordMatrix.putRow(i,Transforms.unitVec(vec));
        }


        Map<String,INDArray> cpcVectors = toPredictMap;

        AtomicInteger incomplete = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);

        System.out.println("Starting predictions...");

        Map<String,Set<String>> predictions = Collections.synchronizedMap(new HashMap<>());
        cpcVectors.entrySet().parallelStream().forEach(e->{
            MinHeap<WordFrequencyPair<MultiStem,Double>> heap = new MinHeap<>(maxTags);

            String cpc = e.getKey();
            INDArray cpcVec = Transforms.unitVec(e.getValue());

            float[] scores = keywordMatrix.mulRowVector(cpcVec).sum(1).data().asFloat();
            for(int i = 0; i < scores.length; i++) {
                double score = scores[i];
                if(score>=minScore) {
                    heap.add(new WordFrequencyPair<>(keywords.get(i), score));
                }
            }

            Set<String> tags = Collections.synchronizedSet(new HashSet<>(maxTags));

            while(!heap.isEmpty()) {
                MultiStem keyword = heap.remove().getFirst();
                tags.add(keyword.getBestPhrase());
            }

            if(!tags.isEmpty()) {
                predictions.put(cpc, tags);
            } else {
                incomplete.getAndIncrement();
            }


            if(cnt.getAndIncrement()%100==99) {
                System.gc();
                System.out.println("Best keywords for "+cpc+": "+String.join("; ",tags));
                System.out.println("Finished "+cnt.get()+" out of "+cpcVectors.size()+". Incomplete: "+incomplete.get()+"/"+cnt.get());
            }
        });

        System.out.println("size="+predictions.size());
        return predictions;
    }

    @Override
    public Map<String,Set<String>> predict(List<String> assets, List<String> assignees, List<String> cpcs) {
        final double minScore = 0.6d;
        final int maxTags = 5;

        if(keywordToVectorLookupTable==null) {
            System.out.println("Loading keyword to vector table...");
            buildKeywordToLookupTableMap();
        }

        List<MultiStem> keywords = Collections.synchronizedList(new ArrayList<>(keywordToVectorLookupTable.keySet()));
        return predict(keywords,combinedDeepCPC2VecEncodingPipelineManager.getOrLoadCPCVectors(),maxTags,minScore);
    }


    public synchronized void buildKeywordToLookupTableMap() {
        if(keywordToVectorLookupTable==null) {
            keywordToVectorLookupTable = (Map<MultiStem, INDArray>) Database.tryLoadObject(keywordToVectorLookupTableFile);

            if(keywordToVectorLookupTable==null) {

                // get vectors
                keywordToVectorLookupTable = Collections.synchronizedMap(new HashMap<>());
                CombinedDeepCPC2VecEncodingModel simModel = (CombinedDeepCPC2VecEncodingModel)combinedDeepCPC2VecEncodingPipelineManager.getModel();
                Word2Vec word2Vec = combinedDeepCPC2VecEncodingPipelineManager.getWord2Vec();
                multiStemSet.stream().forEach(stem -> {
                    String[] words = stem.getBestPhrase().toLowerCase().split(" ");
                    List<String> valid = Stream.of(words).filter(word2Vec::hasWord).collect(Collectors.toList());
                    if(valid.size()==words.length) {
                        INDArray encoding = simModel.encodeText(valid,6);
                        if (encoding!=null) {
                            keywordToVectorLookupTable.put(stem, encoding);
                        }
                    }
                });

                Database.trySaveObject(keywordToVectorLookupTable,keywordToVectorLookupTableFile);

            } else {
                System.out.println("Using previous keyword to vector lookup table...");
            }

        }
        System.out.println("Stem lookup table size: "+keywordToVectorLookupTable.size());
    }

    @Override
    protected void splitData() {
    }

    @Override
    protected void setDatasetManager() {

    }

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);

        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = true;
        int nEpochs = 10;

        KeyphrasePredictionPipelineManager pipelineManager = new KeyphrasePredictionPipelineManager(CombinedDeepCPC2VecEncodingPipelineManager.getOrLoadManager(true));

        //pipelineManager.initStages(true,false,false,false);
        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }
}
