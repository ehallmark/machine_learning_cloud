package models.keyphrase_prediction;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.models.DefaultModel3;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.*;
import models.similarity_models.paragraph_vectors.FloatFrequencyPair;
import models.similarity_models.word_cpc_2_vec_model.AbstractWordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.ParagraphCPCVecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import tools.MinHeap;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 12/21/17.
 */
public class KeyphrasePredictionPipelineManager extends DefaultPipelineManager<WordCPCIterator,Set<String>> {
    public static final Model modelParams = new DefaultModel3();
    @Getter
    private AbstractWordCPC2VecPipelineManager wordCPC2VecPipelineManager;
    private static final File INPUT_DATA_FOLDER = new File("keyphrase_prediction_input_data/");
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"keyphrase_prediction_model_predictions/predictions_map.jobj");
    private static Map<MultiStem,INDArray> keywordToVectorLookupTable;
    private static final File keywordToVectorLookupTableFile = new File(Constants.DATA_FOLDER+"keyword_to_vector_predictions_lookup_table.jobj");
    @Getter
    private Set<MultiStem> multiStemSet;
    private static Map<String,Collection<CPC>> cpcMap;
    private static Map<String,MultiStem> labelToKeywordMap;
    public KeyphrasePredictionPipelineManager(AbstractWordCPC2VecPipelineManager wordCPC2VecPipelineManager) {
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
        initStages(true,true,false,true);
    }

    public void initStages(boolean vocab, boolean filters, boolean rerunVocab, boolean rerunFilters) {
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

        //// stage 3
        //System.out.println("Pre-grouping data for stage 3...");
        //Stage3 stage3 = new Stage3(cpcDensityStage.get(), modelParams);
        //if(filters) stage3.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        // word order stage
        System.out.println("Pre-grouping data for word order...");
        WordOrderStage wordOrder = new WordOrderStage(cpcDensityStage.get(), stage1.get(), modelParams);
        if(filters)wordOrder.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        // kmeans stage
        System.out.println("Pre-grouping data for kNN stage...");
        wordCPC2VecPipelineManager.runPipeline(false,false,false,false,-1,false);
        Word2Vec word2Vec = (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet();
        KNNStage knnStage = new KNNStage(wordOrder.get(), word2Vec, stage1.get(), modelParams);
        knnStage.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();
        multiStemSet = knnStage.get();
       // multiStemSet = wordOrder.get();

        labelToKeywordMap = Collections.synchronizedMap(new HashMap<>());
        multiStemSet.parallelStream().forEach(stem->labelToKeywordMap.put(stem.getBestPhrase(),stem));

        System.out.println("Final num multistems: "+multiStemSet.size());
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
        Map<String,Set<String>> predictions = Collections.synchronizedMap(new HashMap<>());
        Consumer<Pair<String,Set<String>>> consumer = pair -> {
            predictions.put(pair.getFirst(), pair.getSecond());
        };
        predict(keywords,toPredictMap,maxTags,minScore,consumer);
        System.out.println("size="+predictions.size());
        return predictions;
    }

    public void predict(List<MultiStem> keywords, Map<String,INDArray> toPredictMap, int maxTags, double minScore, Consumer<Pair<String,Set<String>>> consumer) {
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
        INDArray cpcMatrix = Nd4j.create(toPredictMap.size(),toPredictMap.values().stream().findAny().get().length());
        List<String> allEntries = new ArrayList<>(toPredictMap.keySet());
        for(int i = 0; i < allEntries.size(); i++) {
            INDArray vec = toPredictMap.get(allEntries.get(i));
            cpcMatrix.putRow(i,Transforms.unitVec(vec));
        }

        final int batchSize = 10000;

        Map<String,INDArray> cpcVectors = toPredictMap;

        AtomicInteger incomplete = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);

        System.out.println("Starting predictions...");

        List<List<String>> entries = batchBy(allEntries,batchSize);
        AtomicInteger idx = new AtomicInteger(0);
        entries.forEach(batch->{
            int i = idx.getAndIncrement();
            INDArray cpcMat = cpcMatrix.get(NDArrayIndex.interval(i*batchSize,i*batchSize+batch.size()),NDArrayIndex.all());
            INDArray simResults = keywordMatrix.mmul(cpcMat.transpose());
            System.out.println("Matrix results shape: "+Arrays.toString(simResults.shape()));
            float[] data = Nd4j.toFlattened('f',simResults).data().asFloat();
            for(int r = 0; r < batch.size(); r++) {
                MinHeap<FloatFrequencyPair<String>> heap = new MinHeap<>(maxTags);
                for(int j = r*keywords.size(); j < r*keywords.size()+keywords.size(); j++) {
                    int kIdx = j%keywords.size();
                    float s = data[j];
                    if(s>=minScore) {
                        heap.add(new FloatFrequencyPair<>(keywords.get(kIdx).getBestPhrase(), s));
                    }
                }

                Set<String> tags = Collections.synchronizedSet(new HashSet<>());
                while(!heap.isEmpty()) {
                    FloatFrequencyPair<String> p = heap.remove();
                    tags.add(p.getFirst());
                }
                String cpc = batch.get(r);
                if(!tags.isEmpty()) {
                    consumer.accept(new Pair<>(cpc,tags));
                } else {
                    incomplete.getAndIncrement();
                }


                if(cnt.getAndIncrement()%100==99) {
                    System.gc();
                    System.out.println("Best keywords for "+cpc+": "+String.join("; ",tags));
                    System.out.println("Finished "+cnt.get()+" out of "+cpcVectors.size()+". Incomplete: "+incomplete.get()+"/"+cnt.get());
                }
            }
        });
    }

    private static List<List<String>> batchBy(List<String> entries, int batch) {
        return IntStream.range(0,1+(entries.size()/batch)).mapToObj(i->{
            if(i*batch>=entries.size())return null;
            return entries.subList(i*batch,Math.min(i*batch+batch,entries.size()));
        }).filter(l->l!=null&&l.size()>0).collect(Collectors.toList());
    }

    @Override
    public Map<String,Set<String>> predict(List<String> assets, List<String> assignees, List<String> cpcs) {
        final double minScore = 0.3;
        final int maxTags = 15;

        if(keywordToVectorLookupTable==null) {
            System.out.println("Loading keyword to vector table...");
            buildKeywordToLookupTableMap();
        }

        List<MultiStem> keywords = Collections.synchronizedList(new ArrayList<>(keywordToVectorLookupTable.keySet()));

        //CPCHierarchy hierarchy = CPCHierarchy.get();
        Map<String,INDArray> cpcPredictions = wordCPC2VecPipelineManager.getOrLoadCPCVectors();
        return predict(keywords,cpcPredictions,maxTags,minScore);
    }


    public synchronized Map<MultiStem,INDArray> buildKeywordToLookupTableMap() {
        if(keywordToVectorLookupTable==null) {
            if(multiStemSet==null) initStages(false,false,false,false);

            keywordToVectorLookupTable = (Map<MultiStem, INDArray>) Database.tryLoadObject(keywordToVectorLookupTableFile);

            if(keywordToVectorLookupTable==null||multiStemSet.size()!=keywordToVectorLookupTable.size()) {
                // get vectors
                Word2Vec word2Vec = (Word2Vec) wordCPC2VecPipelineManager.getModel().getNet();
               keywordToVectorLookupTable = buildNewKeywordToLookupTableMapHelper(word2Vec,multiStemSet);

                Database.trySaveObject(keywordToVectorLookupTable,keywordToVectorLookupTableFile);

            } else {
                System.out.println("Using previous keyword to vector lookup table...");
            }

        }
        System.out.println("Stem lookup table size: "+keywordToVectorLookupTable.size());
        return keywordToVectorLookupTable;
    }

    public static Map<MultiStem,INDArray> buildNewKeywordToLookupTableMapHelper(Word2Vec word2Vec, Set<MultiStem> multiStemSet) {
        Map<MultiStem,INDArray> keywordToVectorLookupTable = Collections.synchronizedMap(new HashMap<>());
        System.out.println("Num vectors to create: "+multiStemSet.size());
        AtomicInteger cnt = new AtomicInteger(0);
        multiStemSet.stream().forEach(stem -> {
            String[] words = stem.getBestPhrase().toLowerCase().split(" ");
            List<String> valid = Stream.of(words).filter(word2Vec::hasWord).collect(Collectors.toList());
            if(valid.size()==words.length) {
                INDArray encoding = Transforms.unitVec(word2Vec.getWordVectors(valid).mean(0));
                if (encoding!=null) {
                    //System.out.println("Shape: "+Arrays.toString(encoding.shape()));
                    keywordToVectorLookupTable.put(stem,encoding);
                }
            }
            if(cnt.getAndIncrement()%100==99) {
                System.out.println(cnt.get());
            }
        });
        return keywordToVectorLookupTable;
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

        WordCPC2VecPipelineManager encodingPipelineManager = new WordCPC2VecPipelineManager(WordCPC2VecPipelineManager.DEEP_MODEL_NAME,-1,-1,-1);
        encodingPipelineManager.runPipeline(false,false,false,false,-1,false);
        KeyphrasePredictionPipelineManager pipelineManager = new KeyphrasePredictionPipelineManager(encodingPipelineManager);

        pipelineManager.initStages(true,true,false,false);

        System.out.println("Num multistem vectors: "+pipelineManager.buildKeywordToLookupTableMap().size());

        pipelineManager.runPipeline(rebuildPrerequisites, rebuildDatasets, runModels, forceRecreateModels, nEpochs, runPredictions);

    }
}
