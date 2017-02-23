package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import com.google.common.util.concurrent.AtomicDouble;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
        GLOBAL_WORD_FREQUENCY_MAP=(Map<String,Double>) Database.tryLoadObject(WordFrequencyCalculator.wordFrequencyMapFile);
        TECHNOLOGY_TO_WORD_FREQUENCY_MAP=(Map<String,Map<String,Double>>) Database.tryLoadObject(WordFrequencyCalculator.technologyToWordFrequencyMapFile);
        ALL_WORDS=new ArrayList<>(GLOBAL_WORD_FREQUENCY_MAP.keySet());
    }

    public static Map<String,Map<String,Double>> getTechnologyToWordFrequencyMap() { return TECHNOLOGY_TO_WORD_FREQUENCY_MAP; }


    /*
     Start of class
     */
    private Map<String,Set<String>> technologyToWordsMap;
    private double fitness;

    public KeywordSolution(Map<String,Set<String>> technologyToWordsMap) {
        this.technologyToWordsMap=technologyToWordsMap;
    }

    public Map<String,Set<String>> getTechnologyToWordsMap() {
        return technologyToWordsMap;
    }

    public List<String> topKeywordsFromTechnology(String tech, int n) {
        if(!technologyToWordsMap.containsKey(tech)) return Collections.emptyList();
        Set<String> keywords = technologyToWordsMap.get(tech);
        Map<String, Double> techToFreqMap = TECHNOLOGY_TO_WORD_FREQUENCY_MAP.get(tech);
        Map<String,Double> scores = new HashMap<>(keywords.size());
        keywords.forEach(word->{
            scores.put(word,tfidfScore(word,techToFreqMap));
        });
        return scores.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .limit(n).map(e->e.getKey()).collect(Collectors.toList());
    }

    @Override
    public double fitness() {
        return fitness;
    }

    @Override
    public void calculateFitness() {
        AtomicDouble score = new AtomicDouble(0d);
        technologyToWordsMap.forEach((tech,words)->{
            if(!words.isEmpty()) {
                AtomicDouble techScore = new AtomicDouble(0d);
                Map<String, Double> techToFreqMap = TECHNOLOGY_TO_WORD_FREQUENCY_MAP.get(tech);
                words.forEach(word -> {
                    techScore.addAndGet(tfidfScore(word, techToFreqMap));
                });
                score.addAndGet(techScore.get() / words.size());
            }
        });
        fitness=score.get()/TECHNOLOGY_TO_WORD_FREQUENCY_MAP.size();
    }

    @Override
    public Solution mutate() {
        Map<String,Set<String>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.forEach((tech,words)->{
            Set<String> newWords = new HashSet<>(words);
            if(!(random.nextBoolean()||random.nextBoolean())) {
                AtomicInteger removedCount = new AtomicInteger(0);
                words.forEach(word -> {
                    if (random.nextBoolean() && random.nextBoolean()) {
                        removedCount.getAndIncrement();
                        newWords.remove(word);
                    }
                });
                int randInt = random.nextInt(1 + removedCount.get() * 2);
                // add random words
                for (int i = 0; i < randInt; i++) {
                    String randomWord = ALL_WORDS.get(random.nextInt(ALL_WORDS.size()));
                    newWords.add(randomWord);
                }
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
                if(random.nextBoolean()) {
                    newSet.add(word);
                }
            });
            ((KeywordSolution)other).technologyToWordsMap.get(tech).forEach(word->{
                if(random.nextBoolean()) {
                    newSet.add(word);
                }
            });
            newTechMap.put(tech,newSet);
        });

        return new KeywordSolution(newTechMap);
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(fitness,o.fitness());
    }

    private static double tfidfScore(String word, Map<String,Double> tech) {
        double score = 0.0;
        if(tech.containsKey(word)&&GLOBAL_WORD_FREQUENCY_MAP.containsKey(word)) {
            score+=tech.get(word)*-Math.log(GLOBAL_WORD_FREQUENCY_MAP.get(word));
        }
        return score/(1+Math.pow(numUnderscores(word),2));
    }

    private static double numUnderscores(String word) {
        AtomicInteger cnt = new AtomicInteger(0);
        word.chars().forEach(c->{
            if(Character.compare((char)c,'_')==0) {
                cnt.getAndIncrement();
            }
        });
        System.out.println("Word size: "+cnt.get());
        return cnt.get();
    }
}
