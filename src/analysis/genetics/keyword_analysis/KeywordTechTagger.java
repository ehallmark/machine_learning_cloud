package analysis.genetics.keyword_analysis;

import analysis.tech_tagger.TechTagger;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import tools.PortfolioList;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 3/9/17.
 */
public class KeywordTechTagger extends TechTagger {
    private static Map<String,List<INDArray>> technologyToKeywordMap;
    static {
        technologyToKeywordMap= (Map<String,List<INDArray>>)Database.tryLoadObject(WordFrequencyCalculator.technologyToTopKeyWordsMapFile);
    }
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
}
