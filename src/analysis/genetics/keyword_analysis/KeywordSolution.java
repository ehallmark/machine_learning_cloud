package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import com.google.common.util.concurrent.AtomicDouble;
import seeding.Database;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/19/2017.
 */
public class KeywordSolution implements Solution {
    public static final int WORDS_PER_TECH = 75;

    private static Random random = new Random(69);
    /*     DATA WE NEED TO PRECOMPUTE IN ORDER FOR THE ALGORITHM TO WORK
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

    public static Map<String,List<Word>> getAllWordsMap() { return ALL_WORD_MAP; }

    /*
     Start of class
     */
    private Map<String,List<Word>> technologyToWordsMap;
    private double fitness;
    private final int wordsPerTechnology;
    private double randFitness;

    public KeywordSolution(Map<String,List<Word>> technologyToWordsMap, int wordsPerTechnology) {
        this.technologyToWordsMap=technologyToWordsMap;
        this.wordsPerTechnology=wordsPerTechnology;
    }

    public Map<String,List<Word>> getTechnologyToWordsMap() {
        return technologyToWordsMap;
    }

    public List<String> topKeywordsFromTechnology(String tech, int n) {
        if(!technologyToWordsMap.containsKey(tech)) return Collections.emptyList();
        List<Word> keywords = technologyToWordsMap.get(tech);
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
            if(words.size()!=wordsPerTechnology) {
                System.out.println("WARNING: TECHNOLOGY HAS INCORRECT WORD SIZE: "+wordsPerTechnology+" but is: "+words.size());
            }
            AtomicDouble techScore = new AtomicDouble(0d);
            words.forEach(word -> {
                techScore.addAndGet(word.getScore());
            });
            score.addAndGet(techScore.get() / words.size());
        });
        fitness=score.get()/TECHNOLOGY_TO_WORD_FREQUENCY_MAP.size();
        randFitness = fitness+fitness*random.nextDouble()/100;
    }

    @Override
    public Solution mutate() {
        Map<String,List<Word>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.forEach((tech,words)->{
            SortedSet<Word> newWords = new TreeSet<>(words);
            int rand = ProbabilityHelper.getLowNumberWithMaxUpTo(newWords.size());
            // add random words
            List<Word> allTechWords = ALL_WORD_MAP.get(tech);
            while(newWords.size()<wordsPerTechnology+rand) {
                newWords.add(allTechWords.get(ProbabilityHelper.getLowNumberWithMaxUpTo(allTechWords.size())));
            }

            List<Word> sortedWordList = new ArrayList<>(newWords);
            while(sortedWordList.size()>wordsPerTechnology) {
                // remove a word towards the end
                int idxToRemove = ProbabilityHelper.getHighNumberWithMaxUpTo(sortedWordList.size());
                sortedWordList.remove(idxToRemove);
            }
            newTechMap.put(tech,sortedWordList);
        });
        KeywordSolution solution =  new KeywordSolution(newTechMap,wordsPerTechnology);
        if(!KeywordSolutionCreator.validateSolution(solution,wordsPerTechnology)) {
            return null;
        }
        return solution;
    }

    @Override
    public Solution crossover(Solution other) {
        Map<String,List<Word>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.keySet().forEach(tech->{
            List<Word> otherWords = ((KeywordSolution)other).technologyToWordsMap.get(tech);
            List<Word> myWords = technologyToWordsMap.get(tech);
            SortedSet<Word> newSet = new TreeSet<>();
            if(random.nextBoolean()) {
                List<Word> toUse;
                if(random.nextBoolean()) {
                    toUse = myWords;
                }else {
                    toUse=otherWords;
                }
                newTechMap.put(tech, toUse);
            } else {
                myWords.forEach(word -> {
                    newSet.add(word);
                });
                otherWords.forEach(word -> {
                    newSet.add(word);
                });
                newTechMap.put(tech,newSet.stream().sequential().limit(wordsPerTechnology).collect(Collectors.toList()));
            }
        });
        KeywordSolution solution = new KeywordSolution(newTechMap,wordsPerTechnology);
        if(!KeywordSolutionCreator.validateSolution(solution,wordsPerTechnology)) {
            return null;
        }
        return solution;
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(((KeywordSolution)o).randFitness,randFitness);
    }

    static double tfidfScore(String word, Map<String,Double> tech) {
        double score = 0.0;
        if(tech.containsKey(word)&&GLOBAL_WORD_FREQUENCY_MAP.containsKey(word)) {
            score+=tech.get(word)*-Math.log(GLOBAL_WORD_FREQUENCY_MAP.get(word));
        }
        return score;
    }
}
