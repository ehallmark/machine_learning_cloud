package value_estimation;

import analysis.tech_tagger.TechTagger;
import tools.PortfolioList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Evan on 2/25/2017.
 */
public class SpecificTechnologyEvaluator extends Evaluator {
    private static final String MODEL_PREFIX = "Value in ";
    private TechTagger tagger;
    private String technology;
    public SpecificTechnologyEvaluator(String technology, TechTagger tagger) {
        super(ValueMapNormalizer.DistributionType.Normal,MODEL_PREFIX+technology);
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
    public double evaluate(String token) {
        PortfolioList.Type quickType;
        quickType= PortfolioList.Type.assignees;
        if(token.length()==7) {
            try {
                Integer.valueOf(token);
                quickType=PortfolioList.Type.patents;
            } catch(Exception e) {
            }
        }
        return tagger.getTechnologyValueFor(token,technology,quickType);
    }
}
