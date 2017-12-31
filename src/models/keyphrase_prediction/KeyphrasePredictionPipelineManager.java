package models.keyphrase_prediction;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.helpers.CombinedModel;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import models.keyphrase_prediction.models.DefaultModel;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.*;
import models.similarity_models.combined_similarity_model.CombinedSimilarityModel;
import models.similarity_models.combined_similarity_model.CombinedSimilarityPipelineManager;
import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.indexaccum.IAMax;
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
    public static final Model modelParams = new DefaultModel();
    private WordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    private static final File INPUT_DATA_FOLDER = new File("keyphrase_prediction_input_data/");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"keyphrase_prediction_model_predictions/predictions_map.jobj");
    private static Map<MultiStem,INDArray> keywordToVectorLookupTable;
    private Set<MultiStem> multiStemSet;
    private static MultiLayerNetwork filingToEncodingNet;
    private static MultiLayerNetwork wordToEncodingNet;
    private static Map<String,Collection<CPC>> cpcMap;
    public KeyphrasePredictionPipelineManager(WordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
        super(INPUT_DATA_FOLDER, PREDICTION_DATA_FILE);
        this.wordCPC2VecPipelineManager=wordCPC2VecPipelineManager;
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
        initStages(false,true);
    }

    private void initStages(boolean rerunVocab, boolean rerunFilters) {
        // stage 1;
        Stage1 stage1 = new Stage1(modelParams);
        stage1.run(rerunVocab);
        //if(alwaysRerun)stage1.createVisualization();

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Stage2 stage2 = new Stage2(stage1.get(), modelParams);
        stage2.run(rerunFilters);
        //if(alwaysRerun)stage2.createVisualization();

        // valid word
        System.out.println("Pre-grouping data for valid word stage...");
        ValidWordStage validWordStage = new ValidWordStage(stage2.get(), modelParams);
        validWordStage.run(rerunFilters);

        // cpc density
        System.out.println("Pre-grouping data for cpc density...");
        CPCHierarchy hierarchy = new CPCHierarchy();
        CPCDensityStage cpcDensityStage = new CPCDensityStage(validWordStage.get(), modelParams, hierarchy);
        cpcDensityStage.run(rerunFilters);
        //if(alwaysRerun) stage4.createVisualization();

        // stage 3
        System.out.println("Pre-grouping data for stage 3...");
        Stage3 stage3 = new Stage3(cpcDensityStage.get(), modelParams);
        stage3.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        multiStemSet = stage3.get();
    }

    @Override
    protected void initModel(boolean forceRecreateModel) {
        final double keyphraseTrimAlpha = 0.9;
        final double minScore = 0.6d;
        final int maxTags = 5;

        System.out.println("Starting to init model...");
        if(multiStemSet==null) initStages(false,false);

        System.out.println("Loading keyword to vector table...");
        if(keywordToVectorLookupTable==null) {
            buildKeywordToLookupTableMap();
        }

        System.out.println("Building keyword vector pairs...");
        List<MultiStem> keywords = Collections.synchronizedList(new ArrayList<>(keywordToVectorLookupTable.keySet()));
        INDArray keywordMatrix = Nd4j.create(keywords.size(),keywordToVectorLookupTable.values().stream().findAny().get().length());
        for(int i = 0; i < keywords.size(); i++) {
            INDArray vec = keywordToVectorLookupTable.get(keywords.get(i));
            keywordMatrix.putRow(i,Transforms.unitVec(vec));
        }


        Map<String,INDArray> cpcVectors = wordCPC2VecPipelineManager.getOrLoadCPCVectors();
        Map<String,INDArray> wordVectors = wordCPC2VecPipelineManager.getOrLoadWordVectors();

        AtomicInteger incomplete = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);

        System.out.println("Starting predictions...");

        Map<String,Set<String>> predictions = Collections.synchronizedMap(new HashMap<>());
        cpcVectors.entrySet().parallelStream().forEach(e->{
            cnt.getAndIncrement();

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
                INDArray multiStemVec = keywordToVectorLookupTable.get(keyword);

                // potentially remove words from keyphrase
                List<Pair<String,INDArray>> wordVectorsPairs = Stream.of(keyword.getBestPhrase().split(" "))
                        .map(word->{
                            if(wordVectors.containsKey(word)) return new Pair<>(word,wordVectors.get(word));
                            else return null;
                        }).filter(pair->pair!=null).collect(Collectors.toList());

                List<Pair<String,Double>> wordSimilarityPairs = wordVectorsPairs.stream()
                        .map(pair->new Pair<>(pair.getFirst(),Transforms.cosineSim(Transforms.unitVec(pair.getSecond()),Transforms.unitVec(cpcVec))))
                        .collect(Collectors.toList());

                double similarityFullPhrase = Transforms.cosineSim(multiStemVec,cpcVec);
                List<String> wordList = wordSimilarityPairs.stream()
                        .filter(pair->pair.getSecond()>=similarityFullPhrase*keyphraseTrimAlpha)
                        .map(pair->pair.getFirst())
                        .collect(Collectors.toList());



                if(!wordList.isEmpty()) {
                    String prediction = String.join(" ",wordList);
                    tags.add(prediction);
                }
            }

            if(!tags.isEmpty()) {
                predictions.put(cpc, tags);
            } else {
                incomplete.getAndIncrement();
            }


            if(cnt.get()%1000==999) {
                System.out.println("Best keywords for "+cpc+": "+String.join("; ",tags));
                System.out.println("Finished "+cnt.get()+" out of "+cpcVectors.size()+". Incomplete: "+incomplete.get()+"/"+cnt.get());
            }
        });

        System.out.println("saving results... size="+predictions.size());
        Database.trySaveObject(predictions,predictionsFile);

    }


    private void loadSimilarityNetworks() {
        String similarityModelName = CombinedSimilarityPipelineManager.MODEL_NAME;
        CombinedSimilarityPipelineManager combinedSimilarityPipelineManager = new CombinedSimilarityPipelineManager(similarityModelName,null,null,null);
        combinedSimilarityPipelineManager.initModel(false);

        CombinedModel<MultiLayerNetwork> combinedModel = (CombinedModel<MultiLayerNetwork>)combinedSimilarityPipelineManager.getModel().getNet();

        filingToEncodingNet = combinedModel.getNameToNetworkMap().get(CombinedSimilarityModel.CPC_VEC_NET);
        wordToEncodingNet = combinedModel.getNameToNetworkMap().get(CombinedSimilarityModel.WORD_CPC_2_VEC);

    }

    private void buildKeywordToLookupTableMap() {
        // get vectors
        wordCPC2VecPipelineManager.runPipeline(false,false,false,false,-1,false);
        Map<String,INDArray> wordVectorMap = wordCPC2VecPipelineManager.getOrLoadWordVectors();
        keywordToVectorLookupTable = Collections.synchronizedMap(new HashMap<>());

        multiStemSet.stream().forEach(stem->{
            String[] words = stem.getBestPhrase().split(" ");
            List<INDArray> wordVectors = Stream.of(words).map(word->wordVectorMap.get(word)).filter(vec->vec!=null).collect(Collectors.toList());
            if(wordVectors.size()>=Math.max(1,words.length-1)) {
                INDArray vec = Transforms.unitVec(Nd4j.vstack(wordVectors).mean(0));
                keywordToVectorLookupTable.put(stem, vec);
            }
        });

        System.out.println("Stem lookup table size: "+keywordToVectorLookupTable.size());
    }

    @Override
    protected void splitData() {
    }

    @Override
    protected void setDatasetManager() {
        if(datasetManager==null) {
        }
    }

    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);

        boolean rebuildPrerequisites = false;
        boolean rebuildDatasets = false;
        boolean runModels = false;
        boolean forceRecreateModels = false;
        boolean runPredictions = false;
        int nEpochs = 10;
        String modelName = modelParams.getModelName();

        String CPC2VecModelName = WordCPC2VecPipelineManager.SMALL_MODEL_NAME;

        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(CPC2VecModelName,-1,-1,-1);
        KeyphrasePredictionPipelineManager pipelineManager = new KeyphrasePredictionPipelineManager(wordCPC2VecPipelineManager);

        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);
    }
}
