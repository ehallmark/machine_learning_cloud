package analysis.genetics.keyword_analysis;

import analysis.genetics.Solution;
import com.google.common.util.concurrent.AtomicDouble;
import org.deeplearning4j.berkeley.Pair;
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
    private static Map<String,List<Pair<String,Double>>> ALL_WORD_MAP;
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
            List<Pair<String,Double>> words = scores.entrySet().stream().sorted((e1, e2)->e2.getValue().compareTo(e1.getValue())).map(e->new Pair<>(e.getKey(),e.getValue())).collect(Collectors.toList());
            ALL_WORD_MAP.put(tech,words);
        });
    }

    public static Map<String,List<Pair<String,Double>>> getAllWordsMap() { return ALL_WORD_MAP; }

    /*
     Start of class
     */
    private Map<String,List<Pair<String,Double>>> technologyToWordsMap;
    private double fitness;
    private final int wordsPerTechnology;
    private double randFitness;

    public KeywordSolution(Map<String,List<Pair<String,Double>>> technologyToWordsMap, int wordsPerTechnology) {
        this.technologyToWordsMap=technologyToWordsMap;
        this.wordsPerTechnology=wordsPerTechnology;
    }

    public Map<String,List<Pair<String,Double>>> getTechnologyToWordsMap() {
        return technologyToWordsMap;
    }

    public List<String> topKeywordsFromTechnology(String tech, int n) {
        if(!technologyToWordsMap.containsKey(tech)) return Collections.emptyList();
        List<Pair<String,Double>> keywords = technologyToWordsMap.get(tech);
        return keywords.stream()
                .limit(n).map(e->e.getFirst()).collect(Collectors.toList());
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
                techScore.addAndGet(word.getSecond());
            });
            score.addAndGet(techScore.get() / words.size());
        });
        fitness=score.get()/TECHNOLOGY_TO_WORD_FREQUENCY_MAP.size();
        randFitness = fitness+fitness*random.nextDouble()/100;
    }

    @Override
    public Solution mutate() {
        Map<String,List<Pair<String,Double>>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.forEach((tech,words)->{
            List<Pair<String,Double>> newWords = new ArrayList<>(words);
            Set<String> alreadyContained = new HashSet<>();
            newWords.forEach(pair->alreadyContained.add(pair.getFirst()));
            int rand = ProbabilityHelper.getLowNumberWithMaxUpTo(newWords.size());
            // add random words
            List<Pair<String,Double>> allTechWords = ALL_WORD_MAP.get(tech);
            while(newWords.size()<wordsPerTechnology+rand) {
                Pair<String,Double> newWord = allTechWords.get(random.nextInt(allTechWords.size()));
                if(!alreadyContained.contains(newWord.getFirst())) {
                    alreadyContained.add(newWord.getFirst());
                    newWords.add(newWord);
                };
            }

            Collections.sort(newWords,(w1,w2)->w2.getSecond().compareTo(w1.getSecond()));
            while(newWords.size()>wordsPerTechnology) {
                // remove a word towards the end
                int idxToRemove = ProbabilityHelper.getHighNumberWithMaxUpTo(newWords.size());
                newWords.remove(idxToRemove);
            }
            newTechMap.put(tech,newWords);
        });
        KeywordSolution solution =  new KeywordSolution(newTechMap,wordsPerTechnology);
        if(!KeywordSolutionCreator.validateSolution(solution,wordsPerTechnology)) {
            return null;
        }
        return solution;
    }

    @Override
    public Solution crossover(Solution other) {
        Map<String,List<Pair<String,Double>>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.keySet().forEach(tech->{
            List<Pair<String,Double>> otherWords = ((KeywordSolution)other).technologyToWordsMap.get(tech);
            List<Pair<String,Double>> myWords = technologyToWordsMap.get(tech);
            List<Pair<String,Double>> newList = new ArrayList<>();
            if(random.nextBoolean()) {
                List<Pair<String,Double>> toUse;
                if(random.nextBoolean()) {
                    toUse = myWords;
                }else {
                    toUse=otherWords;
                }
                newTechMap.put(tech, toUse);
            } else {
                Set<String> alreadyContained = new HashSet<>();
                myWords.forEach(word -> {
                    if(!alreadyContained.contains(word.getFirst())) {
                        alreadyContained.add(word.getFirst());
                        newList.add(word);
                    }
                });
                otherWords.forEach(word -> {
                    if(!alreadyContained.contains(word.getFirst())) {
                        alreadyContained.add(word.getFirst());
                        newList.add(word);
                    }
                });
                newTechMap.put(tech,newList.stream().sorted((w1,w2)->w2.getSecond().compareTo(w1.getSecond())).limit(wordsPerTechnology).collect(Collectors.toList()));
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
