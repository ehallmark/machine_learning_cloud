package seeding.google.tech_tag;

import lombok.Getter;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.DefaultModel4;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.*;
import models.similarity_models.paragraph_vectors.FloatFrequencyPair;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingModel;
import models.similarity_models.rnn_encoding_model.RNNTextEncodingPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 12/21/17.
 */
public class KeyphrasePredictionModel {
    public static final Model modelParams = new DefaultModel4();
    private static Map<MultiStem,Pair<INDArray,INDArray>> keywordToVectorLookupTable;
    private static final File keywordToVectorLookupTableFile = new File(Constants.DATA_FOLDER+"keyword_to_vector_pair_predictions_lookup_table.jobj");
    @Getter
    private Set<MultiStem> multiStemSet;
    private static Map<String,MultiStem> labelToKeywordMap;
    private Word2Vec word2Vec;
    private RNNTextEncodingModel rnnEnc;
    private List<MultiStem> keywords;
    private INDArray w2vMatrix;
    private INDArray rnnMatrix;
    public KeyphrasePredictionModel(Word2Vec word2Vec, RNNTextEncodingModel rnnEnc) {
        this.word2Vec=word2Vec;
        this.rnnEnc=rnnEnc;
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

        // word order stage
        System.out.println("Pre-grouping data for word order...");
        WordOrderStage wordOrder = new WordOrderStage(validWordStage.get(), stage1.get(), modelParams);
        if(filters)wordOrder.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();

        // kmeans stage
        System.out.println("Pre-grouping data for kNN stage...");
        KNNStage knnStage = new KNNStage(wordOrder.get(), word2Vec, stage1.get(), modelParams);
        knnStage.run(rerunFilters);
        //if(alwaysRerun) stage3.createVisualization();
        multiStemSet = knnStage.get();

        labelToKeywordMap = Collections.synchronizedMap(new HashMap<>());
        multiStemSet.parallelStream().forEach(stem->labelToKeywordMap.put(stem.getBestPhrase(),stem));

        System.out.println("Final num multistems: "+multiStemSet.size());

        keywords = new ArrayList<>(multiStemSet);
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
            if(wordVectors.rows()==multiStem.getStems().length) {
                INDArray vec = Transforms.unitVec(wordVectors.mean(0));
                INDArray rnn = rnnEnc.encode(wordVectors.transpose().reshape(1,wordVectors.columns(),wordVectors.rows()));
                keywordToVectorLookupTable.put(multiStem, new Pair<>(vec,rnn));
            } else {
                multiStem = null;
            }
        }
        return multiStem;
    }

    public void predict(List<String> toPredict, INDArray matrix1, INDArray matrix2, int maxTags, double minScore, Consumer<Pair<String,Set<String>>> consumer) {
        if(keywords==null) {
            initStages(false,false,false,false);
        }
        if(keywordToVectorLookupTable==null) {
            System.out.println("Loading keyword to vector table...");
            buildKeywordToLookupTableMap();
        }

        AtomicInteger incomplete = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);

        System.out.println("Starting predictions...");

        //INDArray simResults = w2vMatrix.mmul(matrix1).addi(rnnMatrix.mmul(matrix2));
        INDArray simResults = rnnMatrix.mmul(matrix2);
        System.out.println("Matrix results shape: "+Arrays.toString(simResults.shape()));
        float[] data = Nd4j.toFlattened('f',simResults).data().asFloat();
        if(toPredict.size()*keywords.size()!=data.length) {
            throw new IllegalStateException("Data length: "+data.length+" != # of matrix entries: "+(toPredict.size()*keywords.size()));
        }
        for(int r = 0; r < toPredict.size(); r++) {
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
            String cpc = toPredict.get(r);
            if(!tags.isEmpty()) {
                consumer.accept(new Pair<>(cpc,tags));
            } else {
                incomplete.getAndIncrement();
            }


            if(cnt.getAndIncrement()%100==99) {
                System.gc();
                System.out.println("Best keywords for "+cpc+": "+String.join("; ",tags));
                System.out.println("Finished "+cnt.get()+" out of "+toPredict.size()+". Incomplete: "+incomplete.get()+"/"+cnt.get());
            }
        }
    }

    // first entry is word2vec average, second entry is rnn_enc
    public synchronized Map<MultiStem,Pair<INDArray,INDArray>> buildKeywordToLookupTableMap() {
        if(keywordToVectorLookupTable==null) {
            if(multiStemSet==null) initStages(false,false,false,false);

            keywordToVectorLookupTable = (Map<MultiStem, Pair<INDArray,INDArray>>) Database.tryLoadObject(keywordToVectorLookupTableFile);

            if(keywordToVectorLookupTable==null||multiStemSet.size()!=keywordToVectorLookupTable.size()) {
                // get vectors
               keywordToVectorLookupTable = buildNewKeywordToLookupTableMapHelper(rnnEnc,word2Vec,multiStemSet);

                Database.trySaveObject(keywordToVectorLookupTable,keywordToVectorLookupTableFile);

            } else {
                System.out.println("Using previous keyword to vector lookup table...");
            }

            System.out.println("Building keyword vector pairs...");
            w2vMatrix = Nd4j.create(keywords.size(),keywordToVectorLookupTable.values().stream().findAny().get().getFirst().length());
            rnnMatrix = Nd4j.create(keywords.size(),keywordToVectorLookupTable.values().stream().findAny().get().getSecond().length());
            for(int i = 0; i < keywords.size(); i++) {
                Pair<INDArray,INDArray> vecs = keywordToVectorLookupTable.get(keywords.get(i));
                w2vMatrix.putRow(i,Transforms.unitVec(vecs.getFirst()));
                rnnMatrix.putRow(i,Transforms.unitVec(vecs.getSecond()));
            }

        }
        System.out.println("Stem lookup table size: "+keywordToVectorLookupTable.size());
        return keywordToVectorLookupTable;
    }

    public static Map<MultiStem,Pair<INDArray,INDArray>> buildNewKeywordToLookupTableMapHelper(RNNTextEncodingModel rnnEnc, Word2Vec word2Vec, Set<MultiStem> multiStemSet) {
        Map<MultiStem,Pair<INDArray,INDArray>> keywordToVectorLookupTable = Collections.synchronizedMap(new HashMap<>());
        System.out.println("Num vectors to create: "+multiStemSet.size());
        AtomicInteger cnt = new AtomicInteger(0);
        multiStemSet.stream().forEach(stem -> {
            String[] words = stem.getBestPhrase().toLowerCase().split(" ");
            List<String> valid = Stream.of(words).filter(word2Vec::hasWord).collect(Collectors.toList());
            if(valid.size()==words.length) {
                INDArray vec = word2Vec.getWordVectors(valid);
                INDArray encoding = Transforms.unitVec(vec.mean(0));
                //System.out.println("Shape: "+Arrays.toString(encoding.shape()));
                INDArray rnn = rnnEnc.encode(vec.transpose().reshape(1,vec.columns(),vec.rows()));
                keywordToVectorLookupTable.put(stem,new Pair<>(encoding,rnn));
            }
            if(cnt.getAndIncrement()%100==99) {
                System.out.println(cnt.get());
            }
        });
        return keywordToVectorLookupTable;
    }

    private static KeyphrasePredictionModel MODEL;
    public static synchronized KeyphrasePredictionModel getOrLoadManager(boolean loadWord2Vec) {
        if(MODEL==null) {
            RNNTextEncodingPipelineManager pipelineManager = RNNTextEncodingPipelineManager.getOrLoadManager(loadWord2Vec);
            if(pipelineManager.getModel()==null) {
                pipelineManager.runPipeline(false,false,false, false,-1,false);
            }
            Word2Vec word2Vec = pipelineManager.getWord2Vec();
            MODEL = new KeyphrasePredictionModel(word2Vec,(RNNTextEncodingModel) pipelineManager.getModel());
        }
        return MODEL;
    }


    public static void main(String[] args) {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);

        final boolean vocab = true;
        final boolean stages = true;
        final boolean rerunVocab = false;
        final boolean rerunFilters = false;

        final KeyphrasePredictionModel predictionModel = getOrLoadManager(true);
        predictionModel.initStages(vocab,stages,rerunVocab,rerunFilters);
    }
}
