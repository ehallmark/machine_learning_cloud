package ui_models.attributes.classification;

import similarity_models.paragraph_vectors.SimilarPatentFinder;
import similarity_models.paragraph_vectors.WordFrequencyPair;
import genetics.keyword_analysis.WordFrequencyCalculator;
import seeding.patent_view_api.Patent;
import seeding.patent_view_api.PatentAPIHandler;
import com.google.common.util.concurrent.AtomicDouble;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import tools.MinHeap;
import ui_models.portfolios.AbstractPortfolio;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 3/9/17.
 */
public class KeywordGatherTechTagger extends ClassificationAttr{
    private static Map<String,List<INDArray>> technologyToKeywordMap;
    private static WeightLookupTable<VocabWord> lookupTable;
    static {
        technologyToKeywordMap= (Map<String,List<INDArray>>)Database.tryLoadObject(WordFrequencyCalculator.technologyToTopKeyWordsMapFile);
        lookupTable= SimilarPatentServer.getLookupTable();
    }

    public Collection<String> getClassifications() { return new ArrayList<>(technologyToKeywordMap.keySet()); }

    @Override
    public List<Pair<String, Double>> attributesFor(AbstractPortfolio portfolio, int n) {
        Collection<String> items = portfolio.getTokens();
        INDArray meanVec = requestVectorFromPatents(items);
        MinHeap<WordFrequencyPair<String,Double>> technologyHeap = new MinHeap<>(n);
        technologyToKeywordMap.forEach((tech,vecs) -> {
            int topN = vecs.size()/10;
            technologyHeap.add(new WordFrequencyPair<>(tech,scoreKeywordHeap(vecs,meanVec,topN)));
        });
        List<Pair<String,Double>> predictions = new ArrayList<>(n);
        while(!technologyHeap.isEmpty()) {
            predictions.add(0,technologyHeap.remove().toPair());
        }
        return predictions;
    }

    private INDArray getAvgVectorFromWords(String[] words, String name) {
        SimilarPatentFinder finder = new SimilarPatentFinder(Arrays.asList(words),name,lookupTable);
        if(!finder.getPatentList().isEmpty()) {
            INDArray avg = finder.computeAvg();
            return avg;
        } else return null;
    }

    public int numClassifications() {
        return technologyToKeywordMap.size();
    }

    private double scoreKeywordHeap(List<INDArray> keywordVectorsForTech, INDArray meanVec, int topN) {
        if(meanVec==null)return 1d;
        if(topN <= 0) return 1d;
        MinHeap<Double> keywordHeap = new MinHeap<>(topN);
        keywordVectorsForTech.forEach(keywordTechVec->{
            keywordHeap.add(Transforms.cosineSim(meanVec,keywordTechVec));
        });
        List<Double> values = new ArrayList<>(topN);
        while(!keywordHeap.isEmpty()) {
            values.add(keywordHeap.remove());
        }
        return 3.0+ (2.0 * values.stream().collect(Collectors.averagingDouble(d->d)));
    }

    private INDArray requestVectorFromPatents(Collection<String> items) {
        Collection<Patent> patents = PatentAPIHandler.requestAllPatents(items);
        List<INDArray> patentVectors = new ArrayList<>(patents.size());
        patents.forEach(patent->{
            List<INDArray> vectors = new ArrayList<>(2);
            if(patent==null) return;
            if(patent.getAbstract()!=null) {
                String[] abstractWords = patent.getAbstract().split("\\s+");
                INDArray vec = getAvgVectorFromWords(abstractWords,patent.getPatentNumber()+"_abstract");
                if(vec!=null) {
                    vectors.add(vec);
                }
            }
            if(patent.getAbstract()!=null) {
                String[] titleWords = patent.getInventionTitle().split("\\s+");
                INDArray vec = getAvgVectorFromWords(titleWords,patent.getPatentNumber()+"_title");
                if(vec!=null) {
                    vectors.add(vec);
                }
            }
            if(vectors.isEmpty()) return;
            INDArray meanVec = Nd4j.vstack(vectors).mean(0);
            patentVectors.add(meanVec);
        });
        if(patentVectors.isEmpty()) return null;
        return Nd4j.vstack(patentVectors).mean(0);
    }

    // TODO incorporate testdata and validationdata to get better accuracy
    public static ClassificationAttr trainAndSaveLatestModel(Map<String,Collection<String>> trainingData, Map<String,Collection<String>> testData, Map<String,Collection<String>> validationData) {
        int wordsPerTechnology = 150;
        Map<String,Map<String,Double>> techMap = new HashMap<>();
        Map<String,Double> globalMap = new HashMap<>();
        final int minimumPatentCount = 20;
        trainingData.forEach((tech,patents)->{
            if(patents.size()<minimumPatentCount) return;
            System.out.println("Starting tech: "+tech);
            patents=patents.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            Map<String,Double> frequencyMap = WordFrequencyCalculator.computeGlobalWordFrequencyMap(patents, minimumPatentCount);
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

        techMap.forEach((tech,map)->{
            wordsToRemove.forEach(toRemove->{
                if(map.containsKey(toRemove)) map.remove(toRemove);
            });
        });

        Map<String,List<String>> topTechMap  = new HashMap<>();
        techMap.forEach((tech, map) -> {
            Map<String, Double> scores = new HashMap<>();
            map.keySet().forEach(word -> {
                scores.put(word, WordFrequencyCalculator.tfidfScore(word, map, globalMap));
            });
            List<String> words = scores.entrySet().stream().map(e -> new WordFrequencyPair<>(e.getKey(), e.getValue())).sorted(Comparator.reverseOrder()).map(pair->pair.getFirst()).limit(wordsPerTechnology).collect(Collectors.toList());
            topTechMap.put(tech, words);
            System.out.println("Top words for "+tech+": "+String.join("; ",words.subList(0,10)));
        });


        System.out.println("Total valid technologies: "+topTechMap.size());

        // take average of the vectors for each word in a keyphrase
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

        // this is the file that does all the magic
        Database.trySaveObject(techToKeywordVectorsMap,WordFrequencyCalculator.technologyToTopKeyWordsMapFile);
        technologyToKeywordMap=techToKeywordVectorsMap;
        return new KeywordGatherTechTagger();
    }
}
