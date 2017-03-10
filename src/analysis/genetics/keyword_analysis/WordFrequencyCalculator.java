package analysis.genetics.keyword_analysis;

import analysis.WordFrequencyPair;
import analysis.patent_view_api.PatentAPIHandler;
import com.google.common.util.concurrent.AtomicDouble;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import tools.MinHeap;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/19/2017.
 */
public class WordFrequencyCalculator {
    static final File technologyToTopKeyWordsMapFile = new File("technology_to_top_keyword_map.jobj");

    static double tfidfScore(String word, Map<String,Double> tech, Map<String,Double> globalFrequencyMap) {
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

    public static void main(String[] args) throws Exception {
        int wordsPerTechnology = 250;
        Map<String,Collection<String>> gatherTechMap = Database.getGatherTechMap();
        Map<String,Map<String,Double>> techMap = new HashMap<>();
        Map<String,Double> globalMap = new HashMap<>();
        final int minimumPatentCount = 6;
        gatherTechMap.forEach((tech,patents)->{
            if(patents.size()<minimumPatentCount) return;
            System.out.println("Starting tech: "+tech);
            patents=patents.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            Map<String,Double> frequencyMap = computeGlobalWordFrequencyMap(patents, minimumPatentCount);
            if(frequencyMap!=null&&frequencyMap.size()>wordsPerTechnology*5) {
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
        Set<String> wordsToRemove = new HashSet<>();
        globalMap.forEach((word,freq)->{
            // only allow < 1/2 stop words
            String[] wordSplit = word.split("_");
            boolean shouldRemove=false;
            // remove if stopword is a leading or trailing word
            if(wordSplit.length>0&&(Constants.STOP_WORD_SET.contains(wordSplit[0])||Constants.STOP_WORD_SET.contains(wordSplit[wordSplit.length-1]))) {
                shouldRemove=true;
            }
            if(!shouldRemove) {
                double stopWordPercentage = 0.0;
                for (String inner : wordSplit) {
                    if (Constants.STOP_WORD_SET.contains(inner)) {
                        stopWordPercentage += 1.0;
                    }
                }
                stopWordPercentage /= wordSplit.length;
                if (stopWordPercentage > 0.4) {
                    // probably a bad word?
                    shouldRemove=true;
                }
            }
            if(shouldRemove) {
                wordsToRemove.add(word);
            }
        });

        Map<String,List<WordFrequencyPair<String,Double>>> allWordMap = new HashMap<>();
        techMap.forEach((tech,map)->{
            wordsToRemove.forEach(toRemove->{
                if(map.containsKey(toRemove)) map.remove(toRemove);
            });
        });

        Map<String,List<String>> topTechMap  = new HashMap<>();
        techMap.forEach((tech, map) -> {
            Map<String, Double> scores = new HashMap<>();
            map.keySet().forEach(word -> {
                scores.put(word, tfidfScore(word, map, globalMap));
            });
            List<String> words = scores.entrySet().stream().map(e -> new WordFrequencyPair<>(e.getKey(), e.getValue())).sorted(Comparator.reverseOrder()).map(pair->pair.getFirst()).limit(wordsPerTechnology).collect(Collectors.toList());
            topTechMap.put(tech, words);
            System.out.println("Top words for "+tech+": "+String.join("; ",words.subList(0,10)));
        });


        System.out.println("Total valid technologies: "+topTechMap.size());

        // take average of the vectors for each word in a keyphrase
        System.out.println("Loading lookup table...");
        SimilarPatentServer.loadLookupTable();
        System.out.println("Finished loading.");
        WeightLookupTable<VocabWord> lookupTable = SimilarPatentServer.getLookupTable();
        // build Map<String,List<Pair<String,INDArray>>> techToKeywordVectorsMap
        Map<String,List<INDArray>> techToKeywordVectorsMap = new HashMap<>();

        topTechMap.forEach((tech,keyphrases)->{
            List<INDArray> keywordsForTech = new ArrayList<>(keyphrases.size());
            keyphrases.forEach(keyphrase->{
                String[] split = keyphrase.split("_");
                if(split!=null) {
                    INDArray wVec = Nd4j.zeros(Constants.VECTOR_LENGTH);
                    int count = 0;
                    for(String word : split) {
                        INDArray tmp = lookupTable.vector(word);
                        if(tmp!=null) {
                            count++;
                            wVec.addi(tmp);
                        }
                    }
                    if(count > 0) {
                        wVec.divi(count);
                        keywordsForTech.add(wVec);
                    }
                }
            });
            System.out.println("Found "+keywordsForTech.size()+" vectors in tech: "+tech);
            techToKeywordVectorsMap.put(tech,keywordsForTech);
        });

        Database.trySaveObject(techToKeywordVectorsMap,technologyToTopKeyWordsMapFile);
    }
}
