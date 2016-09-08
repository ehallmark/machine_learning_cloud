package analysis;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.BuildVocabVectorMap;
import seeding.Constants;
import seeding.Database;
import seeding.GetEtsiPatentsList;
import tools.Emailer;
import tools.MinHeap;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/8/16.
 */
public class TestCompDB {
    private static Map<String,INDArray> randomBaseMap;
    private static Map<String,Set<String>> technologyToTestPatentsMap;
    private static List<Patent> testPatents;
    private static Set<String> testPatentNames;

    public static void RunTest(Map<String,List<String>> compDBMap, Map<String,Pair<Float,INDArray>> vocab, String name) throws Exception {
        final int seed = 41;
        randomBaseMap=new HashMap<>();
        testPatents = new ArrayList<>();
        technologyToTestPatentsMap=new HashMap<>();
        testPatentNames=new HashSet<>();
        Random rand = new Random(seed);
        final int invTestRatio = 4;
        System.out.println("Starting to split testing and base data...");
        // split test train
        compDBMap.entrySet().stream().forEach(e->{
            if(e.getValue().size()<= 2) return;
            try {
                List<String> validSet = e.getValue().stream().filter(v->{
                    if(testPatentNames.contains(v))return false;
                    try{
                        return SimilarPatentFinder.getVectorFromDB(v,vocab)!=null;
                    } catch(Exception ex) {
                        ex.printStackTrace();
                        return false;
                    }
                }).distinct().collect(Collectors.toList());
                if(validSet.size()<=2) return;
                Set<Patent> test = Sets.newHashSet(new Patent(validSet.get(0), SimilarPatentFinder.getVectorFromDB(validSet.get(0), vocab)));
                List<Patent> base = Lists.newArrayList(new Patent(validSet.get(1), SimilarPatentFinder.getVectorFromDB(validSet.get(1), vocab)));
                for (int i = 2; i < validSet.size(); i++) {
                    Patent p = new Patent(validSet.get(i), SimilarPatentFinder.getVectorFromDB(validSet.get(i), vocab));
                    if (rand.nextInt(invTestRatio) == 0) test.add(p);
                    else base.add(p);
                    System.out.println(p.getName());
                }
                Set<String> testPatentNames = test.stream().map(b->b.getName()).collect(Collectors.toSet());
                testPatents.addAll(test);
                testPatentNames.addAll(testPatentNames);
                randomBaseMap.put(e.getKey(), SimilarPatentFinder.computeAvg(base,null));
                technologyToTestPatentsMap.put(e.getKey(), testPatentNames);
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        });

        StringJoiner toEmail = new StringJoiner("\n");
        toEmail.add("-- Comparison Test for "+name+" --");
        toEmail.add("Number of classifications considered: "+technologyToTestPatentsMap.size());
        toEmail.add("Overall number of test examples: "+testPatents.size());
        toEmail.add("--");
        System.out.println("Starting to run tests...");
        for(int i : new int[]{1,3,5,10}) {
            System.out.println("Starting test when n="+i);
            test(i,toEmail);
        }
        new Emailer(toEmail.toString());
    }

    public static void main(String[] args) throws Exception{
        Map<String,Pair<Float,INDArray>> vocab = BuildVocabVectorMap.readVocabMap(new File(Constants.BETTER_VOCAB_VECTOR_FILE));
        SimilarPatentFinder finder = new SimilarPatentFinder(vocab);
        Map<String,List<String>> compdbMap = Database.getCompDBMap();
        Database.setupSeedConn();
        RunTest(compdbMap,vocab, "CompDB Technologies");
        RunTest(GetEtsiPatentsList.getETSIPatentMap(),vocab, "ETSI Standards");
    }

    private static void test(int n,StringJoiner toEmail) {
        AtomicInteger overallCorrect = new AtomicInteger(0);
        getTestPredictions(n).forEach(p->{
            for (String prediction : p.getFirst()) {
                if (technologyToTestPatentsMap.get(prediction).contains(p.getSecond())) {
                    overallCorrect.getAndIncrement();
                    break;
                }
            }
        });
        toEmail.add("--");
        toEmail.add("Number of allowed guesses: "+n);
        toEmail.add("Overall number of examples correct: "+overallCorrect.get());
        toEmail.add("Overall Correct: %"+((new Double(overallCorrect.get())/testPatents.size())*100.0));
    }

    private static List<Pair<List<String>,String>> getTestPredictions(int n) {
        List<MinHeap<WordFrequencyPair<String,Double>>> heaps = new ArrayList<>(testPatents.size());
        for(int i = 0; i < testPatents.size(); i++) {
            heaps.add(new MinHeap<>(n));
        }
        randomBaseMap.entrySet().forEach(e->{
            System.out.println(e.getKey());
            for(int i = 0; i < heaps.size(); i++) {
                INDArray vec = testPatents.get(i).getVector();
                heaps.get(i).add(new WordFrequencyPair<>(e.getKey(), Transforms.cosineSim(vec,e.getValue())));
            }
        });
        List<Pair<List<String>,String>> toReturn = new ArrayList<>();
        for(int i = 0; i < heaps.size(); i++) {
            MinHeap<WordFrequencyPair<String,Double>> heap = heaps.get(i);
            List<String> predictions = new ArrayList<>();
            while (!heap.isEmpty()) {
                predictions.add(0, heap.remove().getFirst());
            }
            toReturn.add(new Pair<>(predictions,testPatents.get(i).getName()));
        }
        return toReturn;
    }

}
