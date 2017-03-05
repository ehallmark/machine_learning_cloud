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
    public static final int WORDS_PER_TECH = 30;

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
            if(map.size()<WORDS_PER_TECH*10)return;
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
    private Map<String,Set<String>> techWordSets;
    private double fitness;
    private final int wordsPerTechnology;
    private double randFitness;

    public KeywordSolution(Map<String,List<Word>> technologyToWordsMap, Map<String,Set<String>> techWordSets, int wordsPerTechnology) {
        this.technologyToWordsMap=technologyToWordsMap;
        this.wordsPerTechnology=wordsPerTechnology;
        this.techWordSets = techWordSets;
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
        Map<String,Set<String>> alreadyAddedMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.forEach((tech,words)->{
            SortedSet<Word> newWords = new TreeSet<>(words);
            int rand = ProbabilityHelper.getHighNumberWithMaxUpTo(newWords.size());
            // add random words
            Set<String> wordSet = new HashSet<>(techWordSets.get(tech));
            List<Word> allTechWords = ALL_WORD_MAP.get(tech);
            for (int i = 0; i < rand; i++) {
                Word randomWord = allTechWords.get(ProbabilityHelper.getLowNumberWithMaxUpTo(allTechWords.size()));
                if(!wordSet.contains(randomWord.getWord())) {
                    newWords.add(randomWord);
                    wordSet.add(randomWord.getWord());
                }
            }
            List<Word> sortedWordList = new ArrayList<>(newWords);
            while(newWords.size()>wordsPerTechnology+1) {
                // remove a word towards the end
                int idxToRemove = ProbabilityHelper.getHighNumberWithMaxUpTo(sortedWordList.size());
                wordSet.remove(sortedWordList.remove(idxToRemove).getWord());
            }

            newTechMap.put(tech,sortedWordList);
            alreadyAddedMap.put(tech,wordSet);
        });
        return new KeywordSolution(newTechMap,alreadyAddedMap,wordsPerTechnology);
    }

    @Override
    public Solution crossover(Solution other) {
        Map<String,List<Word>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        Map<String,Set<String>> alreadyAddedMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.keySet().forEach(tech->{
            List<Word> otherWords = ((KeywordSolution)other).technologyToWordsMap.get(tech);
            List<Word> myWords = technologyToWordsMap.get(tech);
            SortedSet<Word> newSet = new TreeSet<>();
            Set<String> alreadyAdded = new HashSet<>(wordsPerTechnology*2);
            if(random.nextBoolean()&&random.nextBoolean()) {
                List<Word> toUse;
                if(random.nextBoolean()) {
                    toUse = myWords;
                }else {
                    toUse=otherWords;
                }
                toUse.forEach(word->{
                    alreadyAdded.add(word.getWord());
                });
                newTechMap.put(tech, toUse);
            } else {

                myWords.forEach(word -> {
                    if (!alreadyAdded.contains(word.getWord())) {
                        alreadyAdded.add(word.getWord());
                        newSet.add(word);
                    }
                });
                otherWords.forEach(word -> {
                    if (!alreadyAdded.contains(word.getWord())) {
                        alreadyAdded.add(word.getWord());
                        newSet.add(word);
                    }
                });
                if (newSet.size() < wordsPerTechnology) {
                    if (random.nextBoolean()) {
                        newTechMap.put(tech, myWords);
                    } else {
                        newTechMap.put(tech, otherWords);
                    }
                } else {
                    List<Word> wordList = newSet.stream().sequential().collect(Collectors.toList());
                    for (int i = wordsPerTechnology; i < wordList.size(); i++) {
                        Word word = wordList.get(i);
                        alreadyAdded.remove(word.getWord());
                    }
                    newTechMap.put(tech, wordList.subList(0, wordsPerTechnology));
                }
            }
            alreadyAddedMap.put(tech,alreadyAdded);
        });
        return new KeywordSolution(newTechMap,alreadyAddedMap,wordsPerTechnology);
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
