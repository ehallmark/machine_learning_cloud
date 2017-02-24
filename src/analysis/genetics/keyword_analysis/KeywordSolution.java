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
            if(map.isEmpty())return;
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
    private final int minWordsPerTechnology;
    private double randFitness;

    public KeywordSolution(Map<String,List<Word>> technologyToWordsMap, Map<String,Set<String>> techWordSets, int minWordsPerTechnology) {
        this.technologyToWordsMap=technologyToWordsMap;
        this.minWordsPerTechnology=minWordsPerTechnology;
        if(techWordSets==null) {
            this.techWordSets=technologyToWordsMap.entrySet().stream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue().stream().map(w->w.getWord()).collect(Collectors.toSet())));
        } else {
            this.techWordSets = techWordSets;
        }
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
            if(!words.isEmpty()) {
                AtomicDouble techScore = new AtomicDouble(0d);
                words.forEach(word -> {
                    techScore.addAndGet(word.getScore());
                });
                score.addAndGet(techScore.get() / words.size());
            }
        });
        fitness=score.get()/TECHNOLOGY_TO_WORD_FREQUENCY_MAP.size();
        randFitness = fitness+fitness*random.nextDouble()/100;
    }

    @Override
    public Solution mutate() {
        Map<String,List<Word>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        Map<String,Set<String>> alreadyAddedMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.forEach((tech,words)->{
            List<Word> newWords = new ArrayList<>(words);
            int rand = ProbabilityHelper.getLowNumberWithMaxUpTo(newWords.size());
            Set<String> wordSet = new HashSet<>(techWordSets.get(tech));
            for(int i = 0; i < rand; i++) {
                // remove a word towards the end
                if(newWords.isEmpty()) break;
                int idxToRemove = ProbabilityHelper.getHighNumberWithMaxUpTo(newWords.size());
                wordSet.remove(newWords.remove(idxToRemove).getWord());
            }

            /// add words back in
            rand = ProbabilityHelper.getLowNumberWithMaxUpTo(newWords.size());
            // add random words
            List<Word> allTechWords = ALL_WORD_MAP.get(tech);
            for (int i = 0; i < rand; i++) {
                Word randomWord = allTechWords.get(ProbabilityHelper.getLowNumberWithMaxUpTo(allTechWords.size()));
                if(!wordSet.contains(randomWord.getWord())) {
                    newWords.add(randomWord);
                    wordSet.add(randomWord.getWord());
                }
            }

            newTechMap.put(tech,newWords);
            alreadyAddedMap.put(tech,wordSet);
        });
        return new KeywordSolution(newTechMap,alreadyAddedMap,minWordsPerTechnology);
    }

    @Override
    public Solution crossover(Solution other) {
        Map<String,List<Word>> newTechMap = new HashMap<>(technologyToWordsMap.size());
        Map<String,Set<String>> alreadyAddedMap = new HashMap<>(technologyToWordsMap.size());
        technologyToWordsMap.keySet().forEach(tech->{
            List<Word> otherWords = ((KeywordSolution)other).technologyToWordsMap.get(tech);
            List<Word> myWords = technologyToWordsMap.get(tech);
            int size = Math.max((otherWords.size()+myWords.size())/2,minWordsPerTechnology);
            SortedSet<Word> newSet = new TreeSet<>();
            Set<String> alreadyAdded = new HashSet<>(size*2);
            myWords.forEach(word->{
                if(!alreadyAdded.contains(word.getWord())) {
                    alreadyAdded.add(word.getWord());
                    newSet.add(word);
                }
            });
            otherWords.forEach(word->{
                if(!alreadyAdded.contains(word.getWord())) {
                    alreadyAdded.add(word.getWord());
                    newSet.add(word);
                }
            });
            newTechMap.put(tech,newSet.stream().limit(size).collect(Collectors.toList()));
            alreadyAddedMap.put(tech,alreadyAdded);
        });
        return new KeywordSolution(newTechMap,alreadyAddedMap,minWordsPerTechnology);
    }

    @Override
    public int compareTo(Solution o) {
        return Double.compare(randFitness,((KeywordSolution)o).randFitness);
    }

    static double tfidfScore(String word, Map<String,Double> tech) {
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
