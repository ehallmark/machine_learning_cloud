package models.similarity_models.combined_similarity_model;

import com.google.common.util.concurrent.AtomicDouble;
import data_pipeline.helpers.Function2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;
import tools.MinHeap;
import user_interface.ui_models.engines.TextSimilarityEngine;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestTextModels {



    public static double testModel(Map<String,Pair<String[],Set<String>>> keywordToWikiAndAssetsMap, Function2<String[],Integer,Set<String>> model, int n) {
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicDouble sum = new AtomicDouble(0d);
        keywordToWikiAndAssetsMap.forEach((keyword,pair)->{
            String[] text = pair.getFirst();
            Set<String> predictions = model.apply(text,n);
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

    private static int intersect(Collection<String> c1, Collection<String> c2) {
        Set<String> s = new HashSet<>(c1);
        s.removeAll(c2);
        return s.size();
    }

    private static int union(Collection<String> c1, Collection<String> c2) {
        Set<String> s = new HashSet<>(c1);
        s.addAll(c2);
        return s.size();
    }

    private static Set<String> topNByCosineSim(List<String> filings, INDArray filingMatrix, INDArray encodingVec, int n) {
        float[] results = filingMatrix.mmul(Transforms.unitVec(encodingVec).reshape(1,encodingVec.length())).data().asFloat();
        return IntStream.range(0,results.length).mapToObj(i->new Pair<>(filings.get(i),results[i]))
                .sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond()))
                .limit(n)
                .map(p->p.getFirst())
                .collect(Collectors.toSet());
    }

    private static Pair<List<String>,INDArray> createFilingMatrix(Map<String,INDArray> filingToVectorMap) {
        int columns = filingToVectorMap.values().stream().findAny().get().length();
        INDArray mat = Nd4j.create(filingToVectorMap.size(),columns);
        List<String> filings = Collections.synchronizedList(new ArrayList<>(filingToVectorMap.size()));
        for(int i = 0; i < filings.size(); i++) {
            mat.putRow(i, Transforms.unitVec(filingToVectorMap.get(filings.get(i))));
        }
        return new Pair<>(filings,mat);
    }

    public static void main(String[] args) {
        // load input data
        final Map<String,Pair<String[],Set<String>>> keywordToWikiAndAssetsMap = null; // TODO

        // new model
        CombinedCPC2Vec2VAEEncodingPipelineManager encodingPipelineManager1 = CombinedCPC2Vec2VAEEncodingPipelineManager.getOrLoadManager(true);
        encodingPipelineManager1.runPipeline(false,false,false,false,-1,false);
        CombinedCPC2Vec2VAEEncodingModel encodingModel1 = (CombinedCPC2Vec2VAEEncodingModel)encodingPipelineManager1.getModel();
        Map<String,INDArray> predictions1 = null; // TODO

        final Pair<List<String>,INDArray> filingsWithMatrix1 = createFilingMatrix(predictions1);
        final List<String> filings1 = filingsWithMatrix1.getFirst();
        final INDArray filingsMatrix1 = filingsWithMatrix1.getSecond();
        Function2<String[],Integer,Set<String>> model1 = (text,n) -> {
            INDArray encodingVec = encodingModel1.encodeText(Arrays.asList(text),20);
            if(encodingVec==null)return null;
            return topNByCosineSim(filings1,filingsMatrix1,encodingVec,n);
        };


        // older model
        TextSimilarityEngine encodingModel2 = new TextSimilarityEngine();
        Map<String,INDArray> predictions2 = null; // TODO

        final Pair<List<String>,INDArray> filingsWithMatrix2 = createFilingMatrix(predictions2);
        final List<String> filings2 = filingsWithMatrix2.getFirst();
        final INDArray filingsMatrix2 = filingsWithMatrix2.getSecond();
        Function2<String[],Integer,Set<String>> model2 = (text,n) -> {
            INDArray encodingVec = encodingModel2.encodeText(text);
            if(encodingVec==null)return null;
            return topNByCosineSim(filings2,filingsMatrix2,encodingVec,n);
        };


        final int n = 500;
        double score1 = testModel(keywordToWikiAndAssetsMap, model1, n);
        double score2 = testModel(keywordToWikiAndAssetsMap, model2, n);

        System.out.println("Score for model [n="+n+"] 1: "+score1);
        System.out.println("Score for model [n="+n+"] 2: "+score2);
    }
}
