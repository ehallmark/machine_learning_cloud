package analysis.genetics.keyword_analysis;

import analysis.patent_view_api.PatentAPIHandler;
import com.google.common.util.concurrent.AtomicDouble;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/19/2017.
 */
public class WordFrequencyCalculator {
    static final File wordFrequencyMapFile = new File("globalFrequencyMap.jobj");
    static final File technologyToWordFrequencyMapFile = new File("technologyToFrequencyMap.jobj");

    public static Map<String,Double> computeGlobalWordFrequencyMap(Collection<String> patentsToSearchIn) {
        // Get Data
        final int N = 4;
        Map<String,Integer> wordCounts = new HashMap<>();
        AtomicInteger totalCount = new AtomicInteger(0);
        PatentAPIHandler.requestAllPatents(patentsToSearchIn).forEach(patent->{
            String text = patent.getAbstract();
            if(text!=null) {
                Map<String, Integer> wordMap = allNGramsFor(text, N);
                wordMap.forEach((word, count) -> {
                    if (wordCounts.containsKey(word)) {
                        wordCounts.put(word, wordCounts.get(word) + count);
                    } else {
                        wordCounts.put(word, count);
                    }
                    totalCount.getAndAdd(count);
                });
            }
        });
        final int finalCount = totalCount.get();
        Map<String,Double> wordFrequencies = new HashMap<>(wordCounts.size());
        if(finalCount>0) {
            wordCounts.forEach((word, count) -> {
                wordFrequencies.put(word, new Double(count) / finalCount);
            });
            return wordFrequencies;
        }
        return null;
    }

    private static Map<String,Integer> allNGramsFor(String text, int n) {
        List<String> words = Arrays.asList(text.toLowerCase().replaceAll("[-_/\\.]"," ").replaceAll("[^a-z ]","").split("\\s+"));
        Map<String,Integer> wordCounts = new HashMap<>();
        for(int i = 1; i <= n; i++) {
            addNGramsFor(wordCounts,words,i);
        }
        return wordCounts;
    }

    private static void addNGramsFor(Map<String,Integer> wordCounts, List<String> words, int n) {
        for(int i = 0; i < words.size()-n; i++) {
            String word = String.join("_",words.subList(i,i+n));
            if(wordCounts.containsKey(word)) {
                wordCounts.put(word,wordCounts.get(word)+1);
            } else {
                wordCounts.put(word,1);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String,Collection<String>> gatherTechMap = Database.getGatherTechMap();
        Map<String,Map<String,Double>> techMap = new HashMap<>();
        Map<String,Double> globalMap = new HashMap<>();
        final int sampleSize = 50;
        gatherTechMap.forEach((tech,patents)->{
            if(patents.size()<10) return;
            System.out.println("Starting tech: "+tech);
            patents=patents.stream().sorted(Comparator.reverseOrder()).limit(sampleSize).collect(Collectors.toList());
            Map<String,Double> frequencyMap = computeGlobalWordFrequencyMap(patents);
            if(frequencyMap!=null) {
                techMap.put(tech, frequencyMap);
            }
        });
        AtomicDouble weights = new AtomicDouble(0d);
        techMap.forEach((tech,map)->{
            System.out.println("Merging tech: "+tech);
            double weight = (double) (map.size());
            weights.addAndGet(weight);
            map.forEach((word,freq)->{
                double score = freq*weight;
                if(globalMap.containsKey(word)) {
                    globalMap.put(word,globalMap.get(word)+score);
                } else {
                    globalMap.put(word,score);
                }
            });
        });
        // average values
        new ArrayList<>(globalMap.keySet()).forEach(word->{
            globalMap.put(word,globalMap.get(word)/weights.get());
        });

        // get frequency stats
        INDArray vec = Nd4j.create(globalMap.size());
        AtomicInteger idx = new AtomicInteger(0);
        globalMap.forEach((word,freq)->{
            vec.putScalar(idx.getAndIncrement(),freq);
        });
        double stddev = Math.sqrt(vec.varNumber().doubleValue());
        double mean = vec.meanNumber().doubleValue();
        Set<String> wordsToRemove = new HashSet<>();
        globalMap.forEach((word,freq)->{
            double z = ((freq-mean)/stddev);
            if(Constants.STOP_WORD_SET.contains(word) || (z >= -3 && z < -0.5)) {
                // probably good?
                wordsToRemove.add(word);
            }
        });
        techMap.forEach((tech,map)->{
            wordsToRemove.forEach(toRemove->{
                if(map.containsKey(toRemove)) map.remove(toRemove);
            });
        });
        Database.trySaveObject(globalMap,wordFrequencyMapFile);
        Database.trySaveObject(techMap,technologyToWordFrequencyMapFile);
    }
}
