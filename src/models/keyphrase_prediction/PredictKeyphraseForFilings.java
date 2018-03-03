package models.keyphrase_prediction;

import cpc_normalization.CPC;
import models.similarity_models.paragraph_vectors.WordFrequencyPair;
import models.similarity_models.word_cpc_2_vec_model.WordCPC2VecPipelineManager;
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
        final double minScore = 0.65;

        String CPC2VecModelName = WordCPC2VecPipelineManager.DEEP_MODEL_NAME;
        final int vectorSize = WordCPC2VecPipelineManager.modelNameToVectorSizeMap.get(CPC2VecModelName);
        WordCPC2VecPipelineManager wordCPC2VecPipelineManager = new WordCPC2VecPipelineManager(CPC2VecModelName,-1,-1,-1);
        KeyphrasePredictionPipelineManager pipelineManager = new KeyphrasePredictionPipelineManager(wordCPC2VecPipelineManager);
        pipelineManager.runPipeline(false,false,false,false,-1,false);

        Map<String,Set<String>> cpcToKeyphraseMap = pipelineManager.loadPredictions();
        Map<String,Collection<CPC>> filingToCPCMap = pipelineManager.getCPCMap();

        AtomicInteger incomplete = new AtomicInteger(0);
        AtomicInteger cnt = new AtomicInteger(0);
        Map<String,INDArray> cpcVectors = wordCPC2VecPipelineManager.getOrLoadCPCVectors();
        pipelineManager.buildKeywordToLookupTableMap();
        Map<MultiStem,INDArray> _keyphraseVectors = pipelineManager.buildKeywordToLookupTableMap();
        Map<String,INDArray> keyphraseVectors = Collections.synchronizedMap(new HashMap<>());
        _keyphraseVectors.entrySet().parallelStream().forEach(e->{
            keyphraseVectors.put(e.getKey().getBestPhrase(),e.getValue());
        });

        System.out.println("Building cpc matrix...");
        Map<String,Integer> cpcToIdx = new HashMap<>();
        AtomicInteger idx = new AtomicInteger(0);
        INDArray cpcMatrix = Nd4j.create(cpcVectors.size(),vectorSize);
        cpcVectors.entrySet().forEach(e->{
            int i = idx.getAndIncrement();
            cpcToIdx.put(e.getKey(),i);
            cpcMatrix.putRow(i,Transforms.unitVec(e.getValue()));
        });
        idx.set(0);
        System.out.println("Building keyphrase matrix...");
        Map<String,Integer> keyphraseToIdx = new HashMap<>();
        INDArray keyphraseMatrix = Nd4j.create(keyphraseVectors.size(),vectorSize);
        keyphraseVectors.entrySet().forEach(e->{
            int i = idx.getAndIncrement();
            keyphraseToIdx.put(e.getKey(),i);
            keyphraseMatrix.putRow(i,Transforms.unitVec(e.getValue()));
        });
        System.out.println("Finished");

        technologyMap = Collections.synchronizedMap(filingToCPCMap.entrySet().parallelStream()
                .map(e->{
                    List<String> technologies = Collections.synchronizedList(new ArrayList<>(maxTags));
                    String filing = e.getKey();

                    Collection<CPC> cpcs = e.getValue();
                    List<String> validCPCs = cpcs.stream().filter(cpc->cpc.getNumParts()>=3).filter(cpc->cpcToIdx.containsKey(cpc.getName())).map(cpc->cpc.getName()).collect(Collectors.toList());
                    if(validCPCs.size()>0) {
                        int[] cpcIndices = validCPCs.stream().mapToInt(cpc->cpcToIdx.get(cpc)).toArray();
                        INDArray cpcVec = Nd4j.pullRows(cpcMatrix,1,cpcIndices);
                        Map<String, Long> keyphraseCounts = cpcs.stream().flatMap(cpc -> cpcToKeyphraseMap.getOrDefault(cpc.getName(), Collections.emptySet()).stream()).collect(Collectors.groupingBy(i -> i, Collectors.counting()));

                        List<String> phrases = keyphraseCounts.keySet().stream().filter(p->keyphraseToIdx.containsKey(p)).collect(Collectors.toList());

                        if (phrases.size() > 0) {
                            int[] keyphraseIndices = phrases.stream().mapToInt(p->keyphraseToIdx.get(p)).toArray();
                            INDArray phraseMatrix = Nd4j.pullRows(keyphraseMatrix,1, keyphraseIndices);
                            MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(maxTags);

                            float[] scores = phraseMatrix.mmul(cpcVec.transpose()).max(1).data().asFloat();
                            for(int i = 0; i < scores.length; i++) {
                                double score = scores[i];
                                String phrase = phrases.get(i);
                                score = score+0.1*keyphraseCounts.get(phrase);
                                if(score>=minScore) {
                                    heap.add(new WordFrequencyPair<>(phrase, score));
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
                }).filter(pair->pair!=null).collect(Collectors.toConcurrentMap(e->e.getFirst(),e->e.getSecond())));


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
