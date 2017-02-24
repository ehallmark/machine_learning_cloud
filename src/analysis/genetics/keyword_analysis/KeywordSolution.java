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
    private static Map<String,List<Word>> ALL_WORD_MAP;
    private static Map<String,Map<String,Double>> TECHNOLOGY_TO_WORD_FREQUENCY_MAP;
    static {
        GLOBAL_WORD_FREQUENCY_MAP=(Map<String,Double>) Database.tryLoadObject(WordFrequencyCalculator.wordFrequencyMapFile);
        TECHNOLOGY_TO_WORD_FREQUENCY_MAP=(Map<String,Map<String,Double>>) Database.tryLoadObject(WordFrequencyCalculator.technologyToWordFrequencyMapFile);
        ALL_WORD_MAP=new HashMap<>(TECHNOLOGY_TO_WORD_FREQUENCY_MAP.size());
        TECHNOLOGY_TO_WORD_FREQUENCY_MAP.forEach((tech,map)->{
            Map<String,Double> scores = new HashMap<>();
            map.keySet().forEach(word->{
                scores.put(word,tfidfScore(word,map));
            });
            List<Word> words = scores.entrySet().stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).map(e->new Word(e.getKey(),e.getValue())).collect(Collectors.toList());
            ALL_WORD_MAP.put(tech,words);
        });
    }

    public static Map<String,Map<String,Double>> getTechnologyToWordFrequencyMap() { return TECHNOLOGY_TO_WORD_FREQUENCY_MAP; }


    /*
     Start of class
     */
    private Map<String,SortedSet<Word>> technologyToWordsMap;
    private double fitness;
    private final int minWordsPerTechnology;
    private Map<String,Double> scoreMap;

    public KeywordSolution(Map<String,SortedSet<Word>> technologyToWordsMap, int minWordsPerTechnology) {
        this.technologyToWordsMap=technologyToWordsMap;
        this.minWordsPerTechnology=minWordsPerTechnology;

    }

    public Map<String,SortedSet<Word>> getTechnologyToWordsMap() {
        return technologyToWordsMap;
    }

    public List<String> topKeywordsFromTechnology(String tech, int n) {
        if(!technologyToWordsMap.containsKey(tech)) return Collections.emptyList();
        SortedSet<Word> keywords = technologyToWordsMap.get(tech);
        return keywords.stream()
                .limit(n).map(e->e.getWord()).collect(Collectors.toList());
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
                words.forEach(word -> {
                    techScore.addAndGet(word.getScore());
                });
                score.addAndGet(techScore.get() / words.size());
            }
        });
        fitness=score.get()/TECHNOLOGY_TO_WORD_FREQUENCY_MAP.size();
    }

    @Override
    public Solution mutate() {
        Map<String,SortedSet<Word>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.forEach((tech,words)->{
            SortedSet<Word> newWords = new TreeSet<>(words);
            if(!(random.nextBoolean()||random.nextBoolean())) {
                AtomicInteger removedCount = new AtomicInteger(0);
                words.stream().sorted(Comparator.reverseOrder()).forEach(word -> {
                    if (newWords.size()>=minWordsPerTechnology && random.nextBoolean() && random.nextBoolean() && random.nextBoolean()) {
                        removedCount.getAndIncrement();
                        newWords.remove(word);
                    }
                });
                int randInt = 10+(removedCount.get()/2) +random.nextInt(1 + removedCount.get());
                // add random words
                List<Word> allTechWords = ALL_WORD_MAP.get(tech);
                for (int i = 0; i < randInt; i++) {
                    Word randomWord = allTechWords.get(random.nextInt(allTechWords.size()));
                    newWords.add(randomWord);
                }
            }
            newTechMap.put(tech,newWords);

        });
        return new KeywordSolution(newTechMap,minWordsPerTechnology);
    }

    @Override
    public Solution crossover(Solution other) {
        Map<String,SortedSet<Word>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.keySet().forEach(tech->{
            SortedSet<Word> newSet = new TreeSet<>();
            ((KeywordSolution)other).technologyToWordsMap.get(tech).forEach(word->{
                if(random.nextBoolean()||newSet.size()<minWordsPerTechnology/2) {
                    newSet.add(word);
                }
            });
            technologyToWordsMap.get(tech).forEach(word->{
                if(random.nextBoolean()||newSet.size()<minWordsPerTechnology) {
                    newSet.add(word);
                }
            });
            newTechMap.put(tech,newSet);
        });
        return new KeywordSolution(newTechMap,minWordsPerTechnology);
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
        return score;
    }

    private static double wordCount(String word) {
        AtomicInteger cnt = new AtomicInteger(0);
        word.chars().forEach(c->{
            if(Character.compare((char)c,'_')==0) {
                cnt.getAndIncrement();
            }
        });
        return 1+cnt.get();
    }
}
