package models.similarity_models.combined_similarity_model;

import com.google.common.util.concurrent.AtomicDouble;
import data_pipeline.helpers.Function2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.primitives.Pair;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCPCMap;
import user_interface.ui_models.engines.TextSimilarityEngine;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TestSimModels extends TestModelHelper {

    public static double testModel(Map<String,Pair<Set<String>,Set<String>>> filingsToPositiveAndNegativeFilings, Function2<String,Integer,Set<String>> model, int n) {
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicDouble sum = new AtomicDouble(0d);
        filingsToPositiveAndNegativeFilings.forEach((filing,pair)->{
            Set<String> predictions = model.apply(filing,n);
            if(predictions!=null&&predictions.size()>0) {
                Set<String> actual = pair.getSecond();
                if(actual!=null&&actual.size()>0) {
                    sum.getAndAdd(((double)intersect(predictions,actual))/((double)(union(predictions,actual))));
                }
            }
            cnt.getAndIncrement();
        });
        return sum.get()/cnt.get();
    }

    private static Map<String,Set<String>> loadFilingCPCData(Map<String,Set<String>> cpcMap) {
        return null;
    }

    private static Map<String,Pair<Set<String>,Set<String>>> getPositiveAndNegativeFilingsMap(Map<String,Set<String>> filingToCPCMap, int samples) {
        Map<String,Pair<Set<String>,Set<String>>> map = Collections.synchronizedMap(new HashMap<>());

        List<String> allFilings = new ArrayList<>(filingToCPCMap.keySet());

        System.out.println("All filings: "+allFilings.size());

        Map<String,Set<String>> cpcToFilingMap = Collections.synchronizedMap(new HashMap<>());
        filingToCPCMap.forEach((filing,cpcs)->{
            cpcs.forEach(cpc->{
                cpcToFilingMap.putIfAbsent(cpc,Collections.synchronizedSet(new HashSet<>()));
                cpcToFilingMap.get(cpc).add(filing);

            });
        });

        // sample
        Collections.shuffle(allFilings);
        allFilings = allFilings.subList(0,Math.min(allFilings.size(),samples));

        allFilings.forEach(filing->{
            Set<String> positives = Collections.synchronizedSet(new HashSet<>());
            Set<String> negatives = Collections.synchronizedSet(new HashSet<>());
            Set<String> cpcs = filingToCPCMap.get(filing);
            cpcs.forEach(cpc->{
                Set<String> others = cpcToFilingMap.get(cpc);

            });
            map.put(filing, new Pair<>(positives,negatives));
        });


        return map;
    }

    public static void main(String[] args) {
        // load input data
        Map<String,Set<String>> filingData = loadFilingCPCData(new AssetToCPCMap().getPatentDataMap());


        Set<String> allFilings = Collections.synchronizedSet(new HashSet<>());

        final Map<String,Pair<Set<String>,Set<String>>> filingsToPositiveAndNegativeFilings = null;

        // new model
        CombinedCPC2Vec2VAEEncodingPipelineManager encodingPipelineManager1 = CombinedCPC2Vec2VAEEncodingPipelineManager.getOrLoadManager(true);
        encodingPipelineManager1.runPipeline(false,false,false,false,-1,false);
        CombinedCPC2Vec2VAEEncodingModel encodingModel1 = (CombinedCPC2Vec2VAEEncodingModel)encodingPipelineManager1.getModel();
        Map<String,INDArray> allPredictions1 = CombinedDeepCPC2VecEncodingPipelineManager.getOrLoadManager(false).loadPredictions();
        Map<String,INDArray> predictions1 = allFilings.stream().filter(allPredictions1::containsKey).collect(Collectors.toMap(e->e,e->allPredictions1.get(e)));

        final Pair<List<String>,INDArray> filingsWithMatrix1 = createFilingMatrix(predictions1);
        final List<String> filings1 = filingsWithMatrix1.getFirst();
        final INDArray filingsMatrix1 = filingsWithMatrix1.getSecond();
        Function2<String,Integer,Set<String>> model1 = (text,n) -> {
            INDArray encodingVec = encodingModel1.encodeText(Arrays.asList(text),20);
            if(encodingVec==null)return null;
            return topNByCosineSim(filings1,filingsMatrix1,encodingVec,n);
        };


        // older model
        TextSimilarityEngine encodingModel2 = new TextSimilarityEngine();
        Map<String,INDArray> allPredictions2 = CombinedSimilarityVAEPipelineManager.getOrLoadManager().loadPredictions();
        Map<String,INDArray> predictions2 = allFilings.stream().filter(allPredictions2::containsKey).collect(Collectors.toMap(e->e,e->allPredictions2.get(e)));

        final Pair<List<String>,INDArray> filingsWithMatrix2 = createFilingMatrix(predictions2);
        final List<String> filings2 = filingsWithMatrix2.getFirst();
        final INDArray filingsMatrix2 = filingsWithMatrix2.getSecond();
        Function2<String,Integer,Set<String>> model2 = (text,n) -> {
            INDArray encodingVec = null;//encodingModel2.encodeText(text);
            if(encodingVec==null)return null;
            return topNByCosineSim(filings2,filingsMatrix2,encodingVec,n);
        };


        System.out.println("All relevant filings size: "+allFilings.size());
        System.out.println("Size of filings (Model 1): "+filings1.size());
        System.out.println("Size of filings (Model 2): "+filings2.size());

        for(int n = 10; n <= 1000; n*=10) {
            double score1 = testModel(filingsToPositiveAndNegativeFilings, model1, n);
            double score2 = testModel(filingsToPositiveAndNegativeFilings, model2, n);

            System.out.println("Score for model [n=" + n + "] 1: " + score1);
            System.out.println("Score for model [n=" + n + "] 2: " + score2);
        }
    }
}
