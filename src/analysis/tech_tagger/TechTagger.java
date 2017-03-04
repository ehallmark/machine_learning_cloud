package analysis.tech_tagger;

import org.deeplearning4j.berkeley.Pair;
import tools.PortfolioList;

import java.util.Collection;
import java.util.List;

/**
 * Created by ehallmark on 2/20/17.
 */
public abstract class TechTagger {
    private double weight;
    public void setWeight(double weight) {
        this.weight=weight;
    }
    public double getWeight() { return weight; }
    public abstract double getTechnologyValueFor(String item, String technology);
    public abstract List<Pair<String,Double>> getTechnologiesFor(String item, PortfolioList.Type type, int n);
    public abstract List<Pair<String,Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n);
    public abstract  Collection<String> getAllTechnologies();

}
