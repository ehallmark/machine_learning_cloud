package seeding.google.tech_tag;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import data_pipeline.vectorize.DataSetManager;
import lombok.Getter;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.DefaultModel3;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.*;
import models.similarity_models.paragraph_vectors.FloatFrequencyPair;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.AbstractWordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import models.similarity_models.word_cpc_2_vec_model.WordCPCIterator;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
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
public class KeyphrasePredictionModel {
    public static final Model modelParams = new DefaultModel3();
    private static final File PREDICTION_DATA_FILE = new File(Constants.DATA_FOLDER+"keyphrase_prediction_model_predictions/predictions_map.jobj");
    private static Map<MultiStem,INDArray> keywordToVectorLookupTable;
    private static final File keywordToVectorLookupTableFile = new File(Constants.DATA_FOLDER+"keyword_to_vector_predictions_lookup_table.jobj");
    @Getter
    private Set<MultiStem> multiStemSet;
    private static Map<String,MultiStem> labelToKeywordMap;
    private Word2Vec word2Vec;
    public KeyphrasePredictionModel(Word2Vec word2Vec) {
        this.word2Vec=word2Vec;
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
        KNNStage knnStage = new KNNStage(wordOrder.get(), word2Vec, stage1.get(), modelParams);
        knnStage.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();
        multiStemSet = knnStage.get();
       // multiStemSet = wordOrder.get();

        labelToKeywordMap = Collections.synchronizedMap(new HashMap<>());
        multiStemSet.parallelStream().forEach(stem->labelToKeywordMap.put(stem.getBestPhrase(),stem));

        System.out.println("Final num multistems: "+multiStemSet.size());
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

            // create
            multiStem = new MultiStem(keyword.toLowerCase().split(" "),-1);
            multiStem.setBestPhrase(keyword.toLowerCase());
            INDArray wordVectors = word2Vec.getWordVectors(Arrays.asList(multiStem.getStems()));
            if(wordVectors.rows()>=Math.max(1,multiStem.getStems().length-1)) {
                INDArray vec = Transforms.unitVec(wordVectors.mean(0));
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


    public synchronized Map<MultiStem,INDArray> buildKeywordToLookupTableMap() {
        if(keywordToVectorLookupTable==null) {
            if(multiStemSet==null) initStages(false,false,false,false);

            keywordToVectorLookupTable = (Map<MultiStem, INDArray>) Database.tryLoadObject(keywordToVectorLookupTableFile);

            if(keywordToVectorLookupTable==null||multiStemSet.size()!=keywordToVectorLookupTable.size()) {
                // get vectors
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

    public static Word2Vec getOrLoadManager() {
        String word2VecPath = new File("data/word2vec_model_large.nn256").getAbsolutePath();
        return WordVectorSerializer.readWord2VecModel(word2VecPath);
    }


    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);

        Word2Vec word2Vec = getOrLoadManager();


    }
}
