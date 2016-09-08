package analysis;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.BuildVocabVectorMap;
import seeding.Constants;
import seeding.Database;
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
    private static Map<String,List<String>> compDBMap;
    private static Map<String,INDArray> randomBaseMap;
    private static Map<String,Set<String>> technologyToTestPatentsMap;
    private static Set<Patent> testPatents;
    private static Set<String> testPatentNames;

    public static void main(String[] args) throws Exception{
        final int seed = 41;
        Map<String,Pair<Float,INDArray>> vocab = BuildVocabVectorMap.readVocabMap(new File(Constants.BETTER_VOCAB_VECTOR_FILE));
        SimilarPatentFinder finder = new SimilarPatentFinder(vocab);
        compDBMap=Database.getCompDBMap();
        Database.setupSeedConn();
        randomBaseMap=new HashMap<>();
        testPatents = new HashSet<>();
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

        System.out.println("Starting to run tests...");
        for(int i = 1; i <= 10; i++) {
            System.out.println("Starting test when n="+i);
            test(i);
        }
    }

    private static void test(int n) {
        Set<String> alreadyLooked = new HashSet<>();
        AtomicInteger overallCorrect = new AtomicInteger(0);
        AtomicInteger overallTotal = new AtomicInteger(0);
        testPatents.forEach(p->{
            if(!alreadyLooked.contains(p.getName())) {
                alreadyLooked.add(p.getName());
                List<String> predictions = predictTech(p.getVector(), n);
                for (String prediction : predictions) {
                    if (technologyToTestPatentsMap.get(prediction).contains(p.getName())) {
                        overallCorrect.getAndIncrement();
                        break;
                    }
                }
                overallTotal.getAndIncrement();
            }
        });

        StringJoiner toEmail = new StringJoiner("\n");
        toEmail.add(" -- CompDB Technology Prediction Test (number of guesses="+n+") --");
        toEmail.add("Number of technologies considered: "+technologyToTestPatentsMap.size());
        toEmail.add("Overall number of patents correct: "+overallCorrect.get());
        toEmail.add("Overall number of patents considered: "+overallTotal.get());
        toEmail.add("Overall Correct: %"+((new Double(overallCorrect.get())/overallTotal.get())*100.0));
        new Emailer(toEmail.toString());
    }

    private static List<String> predictTech(INDArray vec, int n) {
        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(n);
        randomBaseMap.entrySet().forEach(e->{
            heap.add(new WordFrequencyPair<>(e.getKey(), Transforms.cosineSim(vec,e.getValue())));
        });
        List<String> toReturn = new ArrayList<>();
        while(!heap.isEmpty()) {
            toReturn.add(0, heap.remove().getFirst());
        }
        return toReturn;
    }
}

class Prediction implements Comparable<Prediction>{
    String name;
    double percentCorrect;
    Prediction(String n, double d) {
        this.name=n;
        this.percentCorrect=d;
    }

    @Override
    public int compareTo(Prediction o) {
        return Double.compare(percentCorrect,o.percentCorrect);
    }
}