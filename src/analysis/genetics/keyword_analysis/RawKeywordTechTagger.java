package analysis.genetics.keyword_analysis;

import analysis.WordFrequencyPair;
import analysis.patent_view_api.Patent;
import analysis.patent_view_api.PatentAPIHandler;
import analysis.tech_tagger.TechTagger;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import server.SimilarPatentServer;
import tools.MinHeap;
import tools.PortfolioList;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 3/10/17.
 */
public class RawKeywordTechTagger extends TechTagger {
    static File raw_keyword_map_file = new File("raw_keyword_to_document_frequency_map.jobj");
    private static Random random = new Random(69);
    private static Map<String,Double> globalFrequencies;
    static {
        if(raw_keyword_map_file.exists()) {
            globalFrequencies=(Map<String,Double>)Database.tryLoadObject(raw_keyword_map_file);
        }
    }

    @Override
    public double getTechnologyValueFor(String item, String technology) {
        return 0d; // NOT POSSIBLE
    }

    @Override
    public int size() {
        return 100;
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(String item, PortfolioList.Type type, int n) {
        return getTechnologiesFor(Arrays.asList(item),type,n);
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        // patent limit = 30
        int patentLimit = 30;
        List<String> patents = new ArrayList<>();
        if(type.equals(PortfolioList.Type.assignees)) {
            System.out.println("IS ASSIGNEE");
            items.forEach(item->patents.addAll(Database.selectPatentNumbersFromAssignee(item)));
        } else {
            System.out.println("IS ASSET");
            patents.addAll(items);
        }
        Set<String> toSearchIn = new HashSet<>(patentLimit);
        if(patents.size()>patentLimit) {
            for(int i = 0; i < patentLimit; i++) toSearchIn.add(patents.remove(random.nextInt(patents.size())));
        }
        Map<String,Double> frequencies = WordFrequencyCalculator.computeGlobalWordFrequencyMap(toSearchIn,0);

        MinHeap<WordFrequencyPair<String,Double>> heap = new MinHeap<>(n);
        frequencies.entrySet().forEach(entry->{
            heap.add(new WordFrequencyPair<>(entry.getKey(),WordFrequencyCalculator.tfidfScore(entry.getKey(),frequencies,globalFrequencies)));
        });
        List<Pair<String,Double>> data = new ArrayList<>(n);
        while(!heap.isEmpty()) {
            data.add(0,heap.remove().toPair());
        }
        return data;
    }

    @Override
    public Collection<String> getAllTechnologies() {
        return Collections.emptyList();
    }



    public static void main(String[] args) {
        final int numRandomPatents = 50000;
        final int maxNum = 9500000;
        final int minNum = 6500000;
        Random rand = new Random(69);
        Set<String> patentStrings = new HashSet<>(numRandomPatents);
        for(int i = 0; i < numRandomPatents; i++) {
            patentStrings.add(String.valueOf(minNum+rand.nextInt(maxNum-minNum)));
        }

        Map<String,Double> frequencyMap = WordFrequencyCalculator.computeGlobalWordFrequencyMap(patentStrings,0);
        if(frequencyMap!=null) {
            Database.trySaveObject(frequencyMap, raw_keyword_map_file);
        }
    }

}
