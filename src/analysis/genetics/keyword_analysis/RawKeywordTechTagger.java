package analysis.genetics.keyword_analysis;

import analysis.patent_view_api.Patent;
import analysis.patent_view_api.PatentAPIHandler;
import analysis.tech_tagger.TechTagger;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import server.SimilarPatentServer;
import tools.PortfolioList;

import java.io.File;
import java.util.*;

/**
 * Created by ehallmark on 3/10/17.
 */
public class RawKeywordTechTagger extends TechTagger {
    static File raw_keyword_map_file = new File("raw_keyword_to_document_frequency_map.jobj");


    @Override
    public double getTechnologyValueFor(String item, String technology) {
        return 0;
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(String item, PortfolioList.Type type, int n) {
        return null;
    }

    @Override
    public List<Pair<String, Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n) {
        return null;
    }

    @Override
    public Collection<String> getAllTechnologies() {
        return null;
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
