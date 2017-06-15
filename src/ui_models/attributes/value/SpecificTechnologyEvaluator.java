package ui_models.attributes.value;

import org.deeplearning4j.berkeley.Pair;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.portfolios.PortfolioList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/25/2017.
 */
public class SpecificTechnologyEvaluator extends ValueAttr {
    private ClassificationAttr tagger;
    private String technology;
    public SpecificTechnologyEvaluator(String technology, ClassificationAttr tagger) {
        super(ValueMapNormalizer.DistributionType.Normal,technology+" Value");
        this.tagger=tagger;
        this.technology=technology;
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        //throw new RuntimeException("Model does not need to be loaded...");
        return new ArrayList<>();
    }

    @Override
    public Map<String,Double> getMap() {
        throw new RuntimeException("There is no model map...");
    }

    @Override
    public Double attributesFor(Collection<String> portfolio, int n) {
        List<Pair<String,Double>> pairs = tagger.attributesFor(portfolio,tagger.numClassifications()).stream().filter(p->technology.equals(p.getFirst())).collect(Collectors.toList());
        if(pairs.isEmpty()) return ValueMapNormalizer.DEFAULT_START;
        else return pairs.stream().collect(Collectors.averagingDouble(p->p.getSecond()));
    }
}
