package models.similarity_models.combined_similarity_model;

import com.google.common.util.concurrent.AtomicDouble;
import data_pipeline.helpers.Function3;
import data_pipeline.pipeline_manager.DefaultPipelineManager;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestModelHelper {
    protected static int intersect(Collection<String> c1, Collection<String> c2) {
        Set<String> s = new HashSet<>(c1);
        s.removeAll(c2);
        return s.size();
    }

    protected static int union(Collection<String> c1, Collection<String> c2) {
        Set<String> s = new HashSet<>(c1);
        s.addAll(c2);
        return s.size();
    }

    public static double testModel(Map<String,Pair<String,String>> filingData, Function3<String,String,String,Double> model) {
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicDouble sum = new AtomicDouble(0d);
        filingData.forEach((filing,pair)->{
            Double result = model.apply(filing,pair.getFirst(),pair.getSecond());
            if(result==null) return;
            sum.addAndGet(result);
            cnt.getAndIncrement();
        });
        return sum.get()/cnt.get();
    }


    protected static double test(DefaultPipelineManager<?,INDArray> pipelineManager, String modelName, Map<String,Pair<String,String>> filingData) {
        final Map<String,INDArray> allPredictions = pipelineManager.loadPredictions();
        AtomicInteger numMissing = new AtomicInteger(0);
        AtomicInteger totalSeen = new AtomicInteger(0);
        Function3<String,String,String,Double> model = (filing, posSample, negSample) -> {
            totalSeen.getAndIncrement();
            if(totalSeen.get()%100==99) {
                System.out.print("-");
            }
            if(totalSeen.get()%1000==999) {
                System.out.println();
            }
            INDArray encodingVec = allPredictions.get(filing);
            INDArray posVec = allPredictions.get(posSample);
            INDArray negVec = allPredictions.get(negSample);
            if(encodingVec==null||posVec==null||negVec==null){
                numMissing.getAndIncrement();
                return null;
            }
            return Transforms.cosineSim(encodingVec,posVec) > Transforms.cosineSim(encodingVec,negVec) ? 1d : 0d;
        };

        double score = testModel(filingData, model);
        System.out.println("Score for model "+modelName+": " + score);
        System.out.println("Missing vectors for "+numMissing.get()+" out of "+totalSeen.get());
        return score;
    }

    protected static Set<String> topNByCosineSim(List<String> filings, INDArray filingMatrix, INDArray encodingVec, int n) {
        float[] results = filingMatrix.mmul(Transforms.unitVec(encodingVec).reshape(encodingVec.length(),1)).data().asFloat();
        if(filings.size()!=results.length) {
            throw new IllegalStateException("Filings size ("+filings.size()+") is not equal to results length ("+results.length+")");
        }
        return IntStream.range(0,results.length).mapToObj(i->new Pair<>(filings.get(i),results[i]))
                .sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond()))
                .limit(n)
                .map(p->p.getFirst())
                .collect(Collectors.toSet());
    }

    protected static Pair<List<String>,INDArray> createFilingMatrix(Map<String,INDArray> filingToVectorMap) {
        int columns = filingToVectorMap.values().stream().findAny().get().length();
        INDArray mat = Nd4j.create(filingToVectorMap.size(),columns);
        List<String> filings = Collections.synchronizedList(new ArrayList<>(filingToVectorMap.keySet()));
        for(int i = 0; i < filings.size(); i++) {
            mat.putRow(i, Transforms.unitVec(filingToVectorMap.get(filings.get(i))));
        }
        return new Pair<>(filings,mat);
    }


    public static void main(String[] args) {
        // test
        Tester tester = new Tester();
        tester.registerModel("Model 1");
        tester.registerModel("Model 2");
        tester.registerModel("Model 3");

        TestSimModels.runTest(tester);
        TestCPCModels.runTest(tester);
        TestAssigneeModels.runTest(tester);

        System.out.println(tester.resultsToString());
    }


}

class Tester {
    SortedMap<String,Map<String,Double>> scores = Collections.synchronizedSortedMap(new TreeMap<>());
    void registerModel(String model) {
        scores.put(model, Collections.synchronizedMap(new HashMap<>()));
    }

    void scoreModel(String model, String testName, double score) {
        scores.get(model).put(testName,score);
    }

    String resultsToString() {
        StringJoiner sj = new StringJoiner("\n","Tester results:\n","\n------------------");
        scores.forEach((model,results)->{
            sj.add("Results for Model "+model+": "+String.join(", ",results.entrySet().stream().map(e->e.getKey()+" -> "+e.getValue()).collect(Collectors.toList())));
        });
        return sj.toString();
    }
}
