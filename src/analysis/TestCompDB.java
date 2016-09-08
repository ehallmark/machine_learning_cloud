package analysis;

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
    private static Map<String,Set<String>> randomBaseNamesMap;
    private static Map<String,Set<Patent>> randomTestMap;

    public static void main(String[] args) throws Exception{
        Database.setupSeedConn();
        Database.setupCompDBConn();
        final int seed = 41;
        Map<String,Pair<Float,INDArray>> vocab = BuildVocabVectorMap.readVocabMap(new File(Constants.BETTER_VOCAB_VECTOR_FILE));
        SimilarPatentFinder finder = new SimilarPatentFinder(vocab);
        compDBMap=Database.getCompDBMap();
        randomBaseMap=new HashMap<>();
        randomTestMap=new HashMap<>();
        randomBaseNamesMap=new HashMap<>();
        Random rand = new Random(seed);
        final int invTestRatio = 4;
        System.out.println("Starting to split testing and base data...");
        // split test train
        compDBMap.entrySet().stream().forEach(e->{
            if(e.getValue().size()<= 2) return;
            try {
                Set<Patent> test = Sets.newHashSet(new Patent(e.getValue().get(0), SimilarPatentFinder.getVectorFromDB(e.getValue().get(0), vocab)));
                List<Patent> base = Arrays.asList(new Patent(e.getValue().get(1), SimilarPatentFinder.getVectorFromDB(e.getValue().get(1), vocab)));
                for (int i = 2; i < e.getValue().size(); i++) {
                    Patent p = new Patent(e.getValue().get(i), SimilarPatentFinder.getVectorFromDB(e.getValue().get(i), vocab));
                    if (rand.nextInt(invTestRatio) == 0) test.add(p);
                    else base.add(p);
                    System.out.println(p.getName());
                }
                randomTestMap.put(e.getKey(), test);
                randomBaseMap.put(e.getKey(), SimilarPatentFinder.computeAvg(base,null));
                randomBaseNamesMap.put(e.getKey(), base.stream().map(b->b.getName()).collect(Collectors.toSet()));
            } catch(Exception ex) {
                ex.printStackTrace();
            }
        });

        System.out.println("Starting to run tests...");
        for(int i = 0; i < 10; i++) {
            System.out.println("Starting test when n="+(i+1));
            test(i);
        }
    }

    private static void test(int n) {
        SortedSet<Prediction> techPercentages = new TreeSet<>();
        Set<String> alreadyLooked = new HashSet<>();
        AtomicInteger overallCorrect = new AtomicInteger(0);
        AtomicInteger overallTotal = new AtomicInteger(0);
        randomTestMap.entrySet().forEach(e->{
            AtomicInteger correctCount = new AtomicInteger(0);
            AtomicInteger total = new AtomicInteger(0);
            for(Patent p : e.getValue()) {
                if(!alreadyLooked.contains(e.getKey())) {
                    alreadyLooked.add(e.getKey());
                    List<String> predictions = predictTech(p.getVector(), n);
                    for (String prediction : predictions) {
                        if (randomBaseNamesMap.get(prediction).contains(p.getName())) {
                            correctCount.getAndIncrement();
                            break;
                        }
                    }
                    total.getAndIncrement();
                }
            }
            overallCorrect.getAndAdd(correctCount.get());
            overallTotal.getAndAdd(total.get());
            techPercentages.add(new Prediction(e.getKey(), new Double(correctCount.get())/total.get()));
        });

        StringJoiner toEmail = new StringJoiner("\n");
        toEmail.add(" -- CompDB Technology Prediction Test (number of guesses="+(n+1)+") --");
        toEmail.add("Overall Correct: %"+((new Double(overallCorrect.get())/overallTotal.get())*100.0));
        for(Prediction p : techPercentages) {
            toEmail.add(p.name+" => %"+(p.percentCorrect)*100.0);
        }
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