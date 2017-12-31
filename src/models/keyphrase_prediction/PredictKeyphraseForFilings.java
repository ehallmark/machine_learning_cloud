package models.keyphrase_prediction;

import ch.qos.logback.classic.Level;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import models.similarity_models.combined_similarity_model.CombinedSimilarityVAEPipelineManager;
import models.similarity_models.cpc_encoding_model.CPCVAEPipelineManager;
import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;
import tools.MinHeap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 11/2/2017.
 */
public class PredictKeyphraseForFilings {
    private static final File technologyMapFile = new File(Constants.DATA_FOLDER+"filing_to_keyphrase_map.jobj");
    private static Map<String,List<String>> technologyMap;
    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.DOUBLE);
        System.setProperty("org.bytedeco.javacpp.maxretries","100");

        final int maxTags = 5;
        final double minScore = 0.5;

        String CPC2VecModelName = WordCPC2VecPipelineManager.SMALL_MODEL_NAME;
        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(CPC2VecModelName,-1,-1,-1);
        KeyphrasePredictionPipelineManager pipelineManager = new KeyphrasePredictionPipelineManager(wordCPC2VecPipelineManager);
        pipelineManager.runPipeline(false,false,false,false,-1,false);

        Map<String,Set<String>> cpcToKeyphraseMap = pipelineManager.loadPredictions();
        Map<String,Collection<CPC>> filingToCPCMap = pipelineManager.getCPCMap();

        Map<String,INDArray> cpcVectors = wordCPC2VecPipelineManager.getOrLoadCPCVectors();
        Map<String,INDArray> wordVectors = wordCPC2VecPipelineManager.getOrLoadWordVectors();

        AtomicInteger incomplete = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);


        technologyMap = Collections.synchronizedMap(filingToCPCMap.entrySet().parallelStream()
                .map(e->{
                    List<String> technologies = Collections.synchronizedList(new ArrayList<>(maxTags));
                    String filing = e.getKey();

                    Collection<CPC> cpcs = e.getValue();
                    List<INDArray> cpcVecs = cpcs.stream().filter(cpc->cpc.getNumParts()>=4).map(cpc->cpcVectors.get(cpc.getName())).filter(vec->vec!=null).collect(Collectors.toList());
                    if(cpcVecs.size()>0) {
                        INDArray cpcVec = Transforms.unitVec(Nd4j.vstack().mean(0));
                        Map<String, Long> keyphraseCounts = cpcs.stream().flatMap(cpc -> cpcToKeyphraseMap.getOrDefault(cpc.getName(), Collections.emptySet()).stream()).collect(Collectors.groupingBy(i -> i, Collectors.counting()));

                        List<Pair<String, INDArray>> phrasesWithVectors = keyphraseCounts.keySet().stream().map(phrase -> {
                            String[] words = phrase.split(" ");
                            List<INDArray> vectors = Stream.of(words).map(word -> wordVectors.get(word)).filter(vec -> vec != null).collect(Collectors.toList());
                            if (vectors.isEmpty()) return null;
                            return new Pair<>(phrase, Transforms.unitVec(Nd4j.vstack(vectors).mean(0)));
                        }).filter(p -> p != null).collect(Collectors.toList());

                        if (phrasesWithVectors.size() > 0) {
                            INDArray phraseMatrix = Nd4j.vstack(phrasesWithVectors.stream().map(p -> p.getSecond()).collect(Collectors.toList()));
                            MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(maxTags);

                            float[] scores = phraseMatrix.mulRowVector(cpcVec).sum(1).data().asFloat();
                            for(int i = 0; i < scores.length; i++) {
                                double score = scores[i];
                                if(score>=minScore) {
                                    String phrase = phrasesWithVectors.get(i).getFirst();
                                    heap.add(new WordFrequencyPair<>(phrase, score*Math.sqrt(keyphraseCounts.get(phrase))));
                                }
                            }

                            while(!heap.isEmpty()) {
                                String keyword = heap.remove().getFirst();
                                technologies.add(0,keyword);
                            }

                        }
                    }
                    if(cnt.getAndIncrement()%10000==9999) {
                        System.out.println("Example "+filing+": "+String.join("; ",technologies));
                        System.out.println("Finished "+cnt.get()+" out of "+filingToCPCMap.size()+". Missing: "+incomplete.get()+" / "+cnt.get());
                    }

                    if(technologies.isEmpty()) {
                        incomplete.getAndIncrement();
                        return null;
                    }
                    return new Pair<>(filing,technologies);
                }).filter(pair->pair!=null).collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond())));


        Database.trySaveObject(technologyMap,technologyMapFile);
    }

    public static synchronized Map<String,List<String>> loadOrGetTechnologyMap() {
        if(technologyMap==null) {
            technologyMap=(Map<String,List<String>>)Database.tryLoadObject(technologyMapFile);
            if(technologyMap==null) {
                System.out.println("No technology filing map found...");
            }
        }
        return technologyMap;
    }

}
