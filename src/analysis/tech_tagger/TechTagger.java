package analysis.tech_tagger;

import org.deeplearning4j.berkeley.Pair;
import tools.PortfolioList;

import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 2/20/17.
 */
public interface TechTagger {
    double getTechnologyValueFor(String item, String technology);
    List<Pair<String,Double>> getTechnologiesFor(String item, PortfolioList.Type type, int n);
    List<Pair<String,Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n);
}
