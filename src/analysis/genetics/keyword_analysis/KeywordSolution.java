package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import com.google.common.util.concurrent.AtomicDouble;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/19/2017.
 */
public class KeywordSolution implements Solution {
    private static Random random = new Random(69);
    /*
     DATA WE NEED TO PRECOMPUTE IN ORDER FOR THE ALGORITHM TO WORK
     */
    private static Map<String,Double> GLOBAL_WORD_FREQUENCY_MAP;
    private static List<String> ALL_WORDS;
    private static Map<String,Map<String,Double>> TECHNOLOGY_TO_WORD_FREQUENCY_MAP;
    static {
        GLOBAL_WORD_FREQUENCY_MAP=(Map<String,Double>) Database.tryLoadObject(new File("globalFrequencyMap.jobj"));
        TECHNOLOGY_TO_WORD_FREQUENCY_MAP=(Map<String,Map<String,Double>>) Database.tryLoadObject(new File("technologyToWordFrequencyMap.jobj"));
        ALL_WORDS=new ArrayList<>(GLOBAL_WORD_FREQUENCY_MAP.keySet());
    }

    /*
     Start of class
     */
    private Map<String,Set<String>> technologyToWordsMap;
    private double fitness;

    public KeywordSolution(Map<String,Set<String>> technologyToWordsMap) {
        this.technologyToWordsMap=technologyToWordsMap;
    }

    @Override
    public double fitness() {
        return fitness;
    }

    @Override
    public void calculateFitness() {
        AtomicDouble score = new AtomicDouble(0d);
        technologyToWordsMap.forEach((tech,words)->{
            AtomicDouble techScore = new AtomicDouble(0d);
            Map<String,Double> techToFreqMap = TECHNOLOGY_TO_WORD_FREQUENCY_MAP.get(tech);
            words.forEach(word->{
                techScore.addAndGet(tfidfScore(word,techToFreqMap));
            });
            score.addAndGet(techScore.get()/words.size());
        });
        fitness=score.get()/TECHNOLOGY_TO_WORD_FREQUENCY_MAP.size();
    }

    @Override
    public Solution mutate() {
        double removalRate = 0.01;
        Map<String,Set<String>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.forEach((tech,words)->{
            AtomicInteger removedCount = new AtomicInteger(0);
            Set<String> newWords = new HashSet<>(words.size());
            words.forEach(word->{
                if(random.nextDouble()<removalRate) {
                    removedCount.getAndIncrement();
                    newWords.add(word);
                }
            });
            int randInt = 1+random.nextInt(removedCount.get()*2);
            // add random words
            for(int i = 0; i < randInt; i++) {
                String randomWord = ALL_WORDS.get(random.nextInt(ALL_WORDS.size()));
                newWords.add(randomWord);
            }
            newTechMap.put(tech,newWords);

        });
        return new KeywordSolution(newTechMap);
    }

    @Override
    public Solution crossover(Solution other) {
        Map<String,Set<String>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.forEach((tech,words)->{
            Set<String> newSet = new HashSet<>();
            words.forEach(word->{
                if(random.nextDouble()<0.5) {
                    newSet.add(word);
                }
            });
            ((KeywordSolution)other).technologyToWordsMap.get(tech).forEach(word->{
                if(random.nextDouble()<0.5) {
                    newSet.add(word);
                }
            });
            newTechMap.put(tech,newSet);
        });

        return new KeywordSolution(newTechMap);
    }

    @Override
    public int compareTo(Solution o) {
        return 0;
    }

    private static double tfidfScore(String word, Map<String,Double> tech) {
        double score = 0.0;
        if(tech.containsKey(word)&&GLOBAL_WORD_FREQUENCY_MAP.containsKey(word)) {
            score+=tech.get(word)*-Math.log(1.0+GLOBAL_WORD_FREQUENCY_MAP.get(word));
        }
        return score;
    }
}
