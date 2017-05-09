package genetics.keyword_analysis;

import seeding.patent_view_api.PatentAPIHandler;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/19/2017.
 */
public class WordFrequencyCalculator {
    public static final File technologyToTopKeyWordsMapFile = new File("data/technology_to_top_keyword_map.jobj");

    public static double tfidfScore(String word, Map<String,Double> tech, Map<String,Double> globalFrequencyMap) {
        Double tf = tech.get(word);
        if(tf==null)tf=0d;
        Double idf = globalFrequencyMap.get(word);
        if(idf==null) idf = 0d;
        return tf*(1.0-Math.pow(idf,0.3));
    }

    public static Map<String,Double> computeGlobalWordFrequencyMap(Collection<String> patentsToSearchIn, int minimum) {
        // Get Data
        final int N = 4;
        AtomicInteger cnt = new AtomicInteger(0);
        Map<String,Integer> wordCounts = new HashMap<>();
        AtomicInteger totalCount = new AtomicInteger(0);
        PatentAPIHandler.requestAllPatents(patentsToSearchIn).forEach(patent->{
            String text = patent.getAbstract();
            if(text!=null) {
                cnt.getAndIncrement();
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
        if(finalCount>0&&cnt.get()>=minimum) {
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

}
