package analysis.tech_tagger;

import org.deeplearning4j.berkeley.Pair;
import tools.PortfolioList;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by ehallmark on 2/20/17.
 */
public abstract class TechTagger {
    private double weight;
    public void setWeight(double weight) {
        this.weight=weight;
    }
    public double getWeight() { return weight; }
    public double getTechnologyValueFor(String item, String technology, PortfolioList.Type type) {
        return getTechnologyValueFor(Arrays.asList(item), technology, type);
    }
    public abstract double getTechnologyValueFor(Collection<String> items, String technology, PortfolioList.Type type);
    public List<Pair<String,Double>> getTechnologiesFor(String item, PortfolioList.Type type, int n) {
        return getTechnologiesFor(Arrays.asList(item),type,n);
    }
    public abstract List<Pair<String,Double>> getTechnologiesFor(Collection<String> items, PortfolioList.Type type, int n);
    public abstract  Collection<String> getAllTechnologies();
    public abstract int size();

}
